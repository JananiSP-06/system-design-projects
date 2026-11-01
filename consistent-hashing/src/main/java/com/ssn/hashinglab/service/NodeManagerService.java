package com.ssn.hashinglab.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ssn.hashinglab.config.HashRingProperties;
import com.ssn.hashinglab.hashing.ConsistentHashRing;
import com.ssn.hashinglab.model.Student;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns:
 *   1) the ConsistentHashRing (the routing logic)
 *   2) one MongoTemplate per physical storage node (the actual connections)
 *
 * Rebalancing is "copy first, delete later": records are copied to their new
 * owner while the OLD ring still serves reads; the ring is swapped only after
 * every copy succeeded, and source records are deleted last. A failure during
 * copying is rolled back and leaves the cluster unchanged.
 */
@Service
public class NodeManagerService {

    private static final Logger log = LoggerFactory.getLogger(NodeManagerService.class);
    private static final String DB_NAME = "studentdb";

    private record Copy(String node, Student student) {}

    private final HashRingProperties properties;

    private final Map<String, MongoTemplate> nodeTemplates = new ConcurrentHashMap<>();
    private final Map<String, MongoClient> nodeClients = new ConcurrentHashMap<>();
    private final Map<String, String> nodeUris = new ConcurrentHashMap<>();
    private volatile ConsistentHashRing ring;

    public NodeManagerService(HashRingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        ring = new ConsistentHashRing(properties.getVirtualNodes());
        for (String raw : properties.getInitialNodes()) {
            String name = raw.trim();
            String uri = properties.getNodeUris().get(name);
            if (uri == null) {
                throw new IllegalStateException("No MongoDB URI configured for node: " + name);
            }
            connectNode(name, uri);
            ring.addNode(name);
            log.info("Registered node [{}] with {} virtual nodes", name, properties.getVirtualNodes());
        }
    }

    private void connectNode(String nodeName, String uri) {
        MongoClient client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(uri))
                        .applyToClusterSettings(b -> b.serverSelectionTimeout(3, TimeUnit.SECONDS))
                        .build());
        nodeClients.put(nodeName, client);
        nodeTemplates.put(nodeName, new MongoTemplate(client, DB_NAME));
        nodeUris.put(nodeName, uri);
    }

    private void disconnectNode(String nodeName) {
        nodeTemplates.remove(nodeName);
        nodeUris.remove(nodeName);
        MongoClient client = nodeClients.remove(nodeName);
        if (client != null) {
            client.close();
        }
    }

    public ConsistentHashRing getRing() {
        return ring;
    }

    public MongoTemplate getTemplate(String nodeName) {
        MongoTemplate t = nodeName == null ? null : nodeTemplates.get(nodeName);
        if (t == null) {
            throw new IllegalStateException("Unknown node: " + nodeName);
        }
        return t;
    }

    public Set<String> getActiveNodes() {
        return new TreeSet<>(nodeTemplates.keySet());
    }

    /**
     * Add a node: connect + verify, copy the records the new node now owns,
     * swap in the new ring, then delete the originals.
     */
    public synchronized Map<String, Object> addNodeDynamically(String nodeNameRaw, String uriRaw) {
        if (nodeNameRaw == null || nodeNameRaw.isBlank() || uriRaw == null || uriRaw.isBlank()) {
            throw new IllegalArgumentException("nodeName and mongoUri are required");
        }
        final String nodeName = nodeNameRaw.trim();
        final String uri = uriRaw.trim();

        if (nodeTemplates.containsKey(nodeName)) {
            throw new IllegalStateException("Node already exists: " + nodeName);
        }
        if (nodeUris.containsValue(uri)) {
            throw new IllegalArgumentException("Another node already uses this MongoDB URI");
        }

        Map<String, Long> before = getDistribution();

        // 1. Connect and verify before touching the ring
        try {
            connectNode(nodeName, uri);
            nodeClients.get(nodeName).getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (Exception e) {
            disconnectNode(nodeName);
            throw new IllegalArgumentException("Cannot connect to MongoDB at " + uri + ": " + e.getMessage());
        }

        // 2. Build the new ring separately; the live ring keeps serving reads
        ConsistentHashRing candidate = new ConsistentHashRing(ring.getVirtualNodesPerNode());
        for (String n : ring.getPhysicalNodes()) {
            candidate.addNode(n);
        }
        candidate.addNode(nodeName);

        // 3. Copy the affected records to the new node (sources untouched)
        MongoTemplate target = nodeTemplates.get(nodeName);
        List<Student> copied = new ArrayList<>();
        Map<String, List<Student>> toDelete = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, MongoTemplate> entry : nodeTemplates.entrySet()) {
                if (entry.getKey().equals(nodeName)) {
                    continue;
                }
                for (Student s : entry.getValue().findAll(Student.class)) {
                    if (nodeName.equals(candidate.getNode(s.getId()))) {
                        target.save(s);
                        copied.add(s);
                        toDelete.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(s);
                    }
                }
            }
        } catch (Exception e) {
            for (Student s : copied) {
                try { target.remove(s); } catch (Exception ignored) { }
            }
            disconnectNode(nodeName);
            throw new IllegalStateException("Adding node failed and was rolled back: " + e.getMessage(), e);
        }

        // 4. Switch routing, then delete the originals
        ring = candidate;
        for (Map.Entry<String, List<Student>> entry : toDelete.entrySet()) {
            MongoTemplate source = nodeTemplates.get(entry.getKey());
            for (Student s : entry.getValue()) {
                source.remove(s);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeAdded", nodeName);
        result.put("recordsMigrated", copied.size());
        result.put("distributionBefore", before);
        result.put("distributionAfter", getDistribution());
        return result;
    }

    /**
     * Remove a node: copy its records to their new owners, swap in the smaller
     * ring, then wipe and disconnect the removed node.
     */
    public synchronized Map<String, Object> removeNodeDynamically(String nodeName) {
        MongoTemplate removed = nodeName == null ? null : nodeTemplates.get(nodeName);
        if (removed == null) {
            throw new NoSuchElementException("Node not found: " + nodeName);
        }
        if (nodeTemplates.size() <= 1) {
            throw new IllegalStateException("Cannot remove the last remaining node");
        }

        Map<String, Long> before = getDistribution();

        ConsistentHashRing candidate = new ConsistentHashRing(ring.getVirtualNodesPerNode());
        for (String n : ring.getPhysicalNodes()) {
            if (!n.equals(nodeName)) {
                candidate.addNode(n);
            }
        }

        List<Student> orphaned = removed.findAll(Student.class);
        List<Copy> copies = new ArrayList<>();
        try {
            for (Student s : orphaned) {
                String owner = candidate.getNode(s.getId());
                nodeTemplates.get(owner).save(s);
                copies.add(new Copy(owner, s));
            }
        } catch (Exception e) {
            for (Copy c : copies) {
                try { nodeTemplates.get(c.node()).remove(c.student()); } catch (Exception ignored) { }
            }
            throw new IllegalStateException("Removing node failed and was rolled back: " + e.getMessage(), e);
        }

        ring = candidate;

        // Don't leave stale copies behind (they would reappear if the node is re-added)
        removed.dropCollection(Student.class);
        disconnectNode(nodeName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeRemoved", nodeName);
        result.put("recordsRedistributed", copies.size());
        result.put("distributionBefore", before);
        result.put("distributionAfter", getDistribution());
        return result;
    }

    public Map<String, Long> getDistribution() {
        Map<String, Long> dist = new TreeMap<>();
        for (Map.Entry<String, MongoTemplate> e : nodeTemplates.entrySet()) {
            dist.put(e.getKey(), e.getValue().count(new Query(), Student.class));
        }
        return dist;
    }
}