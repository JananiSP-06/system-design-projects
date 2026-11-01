# Distributed Data Routing with Consistent Hashing

A Spring Boot application that routes Student records across several
independent MongoDB instances using a **consistent hash ring with virtual
nodes**. Storage nodes can be added or removed at runtime, and only the
affected records are moved.

## Architecture

```
                       POST /api/students
                       GET  /api/students/{id}
                                │
                                ▼
                         StudentService
                                │  hash(student id) → owning node
                                ▼
                       ConsistentHashRing
            (MD5 → 32-bit points, 150 virtual nodes per node)
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
          MongoDB            MongoDB           MongoDB
          node1 :27018       node2 :27019      node3 :27020
                                                       ┌ spare: node4 :27021
                                                       └ (added at runtime)
```

| Component | Role |
|-----------|------|
| `ConsistentHashRing` | Hash ring with virtual nodes; `getNode(key)` walks clockwise to the first point >= hash(key), wrapping around |
| `NodeManagerService` | One `MongoTemplate` per node; adds/removes nodes and migrates only the affected records |
| `StudentService` | Routes each save/read to the node chosen by the ring |
| `NodeController` | `/api/nodes` endpoints to list, add and remove nodes |
| `StudentController` | `/api/students` endpoints to save, fetch and compare distribution |
| `HashRingProperties` | Binds the `hashring` section of `application.yml` |
| `GlobalExceptionHandler` | Maps errors to 400 / 404 / 409 responses |

Spring's default single-database Mongo auto-configuration is excluded on
purpose, because the app talks to several MongoDB instances at once. Each
connection is created manually per node.

## How it works

**Routing.** The student `id` is hashed (MD5, first 4 bytes as an unsigned
32-bit value). The record is stored on the node that owns the first ring point
at or after that hash.

**Virtual nodes.** Each physical node is placed on the ring 150 times
(`nodeName#VN0` ... `#VN149`), which evens out the distribution. With 3 nodes
there are 450 ring points.

**Adding a node.**
1. Connect to the new MongoDB and verify it with a ping.
2. Build a new ring that includes the node (the live ring keeps serving reads).
3. Copy only the records the new node now owns.
4. Switch to the new ring, then delete the originals.
A failure during copying is rolled back and leaves the cluster unchanged.

**Removing a node.**
1. Copy that node's records to their new owners on the smaller ring.
2. Switch to the new ring.
3. Drop the removed node's `students` collection (so no stale copies remain
   if it is added back) and close its connection.

## Why consistent hashing

Measured with this ring on 10,000 keys:

| Test | Result |
|------|--------|
| 3 nodes, 150 virtual nodes each | 3290 / 3245 / 3465 records (even) |
| Add a 4th node | 26.9% of keys moved, all of them to the new node |
| Remove a node | Only that node's keys moved, none from other nodes |
| Same change with modulo hashing (3 to 4 nodes) | about 75% of keys would move |

## Prerequisites
- Java 17+ and Maven
- Docker Desktop (running)

## Run

```bash
docker compose up -d
mvn spring-boot:run
```

App runs on http://localhost:8080. Compose starts four MongoDB containers:
node1 to node3 are in the ring at startup, and `mongo-node4` (port 27021) is a
spare you can add later.

> Host ports 27018 to 27021 and 8080 are also used by other projects in this
> repo (`mongo-sharding`, `rate-limiter`, `autocomplete`). Run one project at a time.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/students` | Save a student on the node chosen by the ring |
| GET | `/api/students/{id}` | Fetch a student (shows which node served it) |
| GET | `/api/students/distribution` | Record count per node |
| GET | `/api/nodes` | Active nodes, virtual nodes per node, total ring points |
| POST | `/api/nodes/add` | Add a node and migrate the affected records |
| DELETE | `/api/nodes/{nodeName}` | Remove a node and redistribute its records |

**Save a student**
```
POST http://localhost:8080/api/students
{ "id": "S1", "name": "Asha", "department": "CSE", "year": 2 }
```
```json
{ "student": { "id": "S1", "name": "Asha", "department": "CSE", "year": 2 }, "storedOnNode": "node2" }
```

**Add a node**
```
POST http://localhost:8080/api/nodes/add
{ "nodeName": "node4", "mongoUri": "mongodb://localhost:27021" }
```
```json
{
  "nodeAdded": "node4",
  "recordsMigrated": 262,
  "distributionBefore": { "node1": 330, "node2": 325, "node3": 345 },
  "distributionAfter":  { "node1": 245, "node2": 250, "node3": 243, "node4": 262 }
}
```
(Example numbers; yours will differ.)

Error responses: `400` for a missing field, an unreachable URI or a URI already
used by another node; `404` for an unknown node on delete; `409` for a duplicate
node name, removing the last node, or a failed (rolled back) migration.

## Demo

**1. Load 1000 students (PowerShell):**
```powershell
1..1000 | ForEach-Object { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/students -ContentType "application/json" -Body (@{id="S$_";name="Student$_";department="CSE";year=2} | ConvertTo-Json) | Out-Null }
```
Bash:
```bash
for i in $(seq 1 1000); do curl -s -X POST localhost:8080/api/students -H "Content-Type: application/json" -d "{\"id\":\"S$i\",\"name\":\"Student$i\",\"department\":\"CSE\",\"year\":2}" > /dev/null; done
```

**2. Distribution before:** `GET /api/students/distribution` (about a third per node).

**3. Add node4:** `POST /api/nodes/add`. About 25% of records move to node4;
the other nodes only lose records, they don't swap them with each other.

**4. Check a lookup:** `GET /api/students/S42` returns the student and
`retrievedFromNode`.

**5. Remove node2:** `DELETE /api/nodes/node2`. Only node2's records are
redistributed.

**6. Distribution after:** `GET /api/students/distribution`.

**Inspect MongoDB directly:**
```bash
docker exec -it mongo-node1 mongosh --eval "db.getSiblingDB('studentdb').students.countDocuments()"
```

## Configuration (`application.yml`)

| Property | Default | Meaning |
|----------|---------|---------|
| `hashring.virtual-nodes` | 150 | Ring points per physical node |
| `hashring.initial-nodes` | node1,node2,node3 | Nodes live at startup |
| `hashring.node-uris.<name>` | mongodb://localhost:2701x | MongoDB URI per node |

Ring membership is held in memory. After a restart, the ring is rebuilt from
`initial-nodes`, so a node added or removed at runtime is not remembered.

## Limitations and possible improvements
- Writes that arrive during a rebalance can land on the old owner. Pause writes
  or use a write-ahead log for production use.
- Ring membership is not persisted. Store it in a config database to survive restarts.
- No replication: each record exists on exactly one node. Replicating to the next
  N nodes on the ring would tolerate node failure.
- Rebalancing loads whole collections into memory; use batched cursors for large data.
- A missing student returns `200` with `null` instead of `404`.
- No authentication on the node management endpoints.