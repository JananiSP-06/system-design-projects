# MongoDB Sharded Cluster (Docker)

A MongoDB sharded cluster running locally with Docker Compose. It shards a
`College.Student` collection of 10,000 documents across 3 shards using
`RollNo` as a range-based shard key.

## Architecture

```
                    ┌──────────────┐
   Client ────────► │    mongos    │  (router, port 27017)
                    └──────┬───────┘
                           │ reads chunk map from
                    ┌──────▼───────┐
                    │ Config server│  (cfgrs, port 27019)
                    └──────────────┘
          routes queries to the right shard(s)
     ┌──────────────┬───────────────┬──────────────┐
     ▼              ▼               ▼
┌─────────┐   ┌─────────┐    ┌─────────┐
│ shard1rs│   │ shard2rs│    │ shard3rs│
│ 1–3333  │   │ 3334–   │    │ 6667–   │
│         │   │ 6666    │    │ 10000   │
└─────────┘   └─────────┘    └─────────┘
```

| Component | Container | Port |
|-----------|-----------|------|
| mongos router | `mongos` | 27017 |
| Config server (`cfgrs`) | `configsvr1` | 27019 |
| Shard 1 (`shard1rs`) | `shard1svr` | 27018 (host 27018) |
| Shard 2 (`shard2rs`) | `shard2svr` | 27018 (host 27020) |
| Shard 3 (`shard3rs`) | `shard3svr` | 27018 (host 27021) |

## Concepts demonstrated
- Sharded cluster components: shards, config server, mongos router
- Range-based sharding on `RollNo`
- Pre-splitting chunks so small datasets distribute across shards
  (the default auto-split threshold is 128MB)
- Targeted queries (use the shard key) vs scatter-gather queries (don't)

## Prerequisites
- Docker Desktop (running)

> Note: `mongos` uses port 27017. Stop any other MongoDB (including the
> URL shortener's) before starting this.

## Run

All commands are run from this folder. PowerShell syntax is shown; in Git Bash
or Linux, replace `Get-Content file |` with `docker exec -i ... < file`.

**1. Start the containers**
```powershell
docker compose up -d
```

**2. Initiate the config server and the 3 shard replica sets**
```powershell
Get-Content init-scripts\init-configsvr.js | docker exec -i configsvr1 mongosh --port 27019
Get-Content init-scripts\init-shard1.js | docker exec -i shard1svr mongosh --port 27018
Get-Content init-scripts\init-shard2.js | docker exec -i shard2svr mongosh --port 27018
Get-Content init-scripts\init-shard3.js | docker exec -i shard3svr mongosh --port 27018
```

**3. Wait about 20 seconds** for the primaries to be elected. Make sure
`mongos` is running with `docker ps`. If it exited, run
`docker compose restart mongos`.

**4. Add the shards and shard the collection**
```powershell
Get-Content init-scripts\init-mongos.js | docker exec -i mongos mongosh
```

**5. Insert the sample data**
```powershell
Get-Content init-scripts\insert-sample-data.js | docker exec -i mongos mongosh
```

## Verify

```bash
docker exec -it mongos mongosh
```
```js
sh.status()
use College
db.Student.getShardDistribution()   // about 3333 documents on each shard

// Targeted query: shard key in the filter, so only one shard is hit
db.Student.find({ RollNo: 5000 }).explain().queryPlanner.winningPlan

// Scatter-gather: no shard key, so all 3 shards are queried
db.Student.find({ Dept: "CSE" }).explain().queryPlanner.winningPlan
```

## Stop and clean up
```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete all data
```

## Design notes and limitations
- `RollNo` increases monotonically, so a range key sends all new inserts to the
  last chunk's shard (a hotspot). `{ RollNo: "hashed" }` would spread writes
  evenly but make range queries hit every shard.
- Each replica set has a single member for simplicity, so there is no
  failover. Production uses 3 members per set.
- There is no authentication or TLS (lab setup only).