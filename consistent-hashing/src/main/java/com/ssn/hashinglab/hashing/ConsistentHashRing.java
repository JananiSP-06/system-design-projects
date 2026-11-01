package com.ssn.hashinglab.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A textbook Consistent Hashing ring with virtual nodes.
 *
 * - Every physical node is mapped onto {virtualNodesPerNode} points on a
 *   32-bit hash circle (0 .. 2^32-1), using MD5 as the hash function.
 * - A key (here, a Student id) is located by hashing the key and walking
 *   CLOCKWISE to the first virtual node point >= hash(key). Wrap-around
 *   is handled by falling back to the first entry in the ring.
 * - Virtual nodes exist purely to smooth out data distribution; without
 *   them a small number of physical nodes produces very uneven ranges.
 */
public class ConsistentHashRing {

    private final int virtualNodesPerNode;
    private final SortedMap<Long, String> ring = new TreeMap<>();

    public ConsistentHashRing(int virtualNodesPerNode) {
        this.virtualNodesPerNode = virtualNodesPerNode;
    }

    /** Adds a physical node to the ring by inserting all of its virtual nodes. */
    public synchronized void addNode(String nodeName) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long hash = hash(nodeName + "#VN" + i);
            ring.put(hash, nodeName);
        }
    }

    /** Removes a physical node from the ring by deleting all of its virtual nodes. */
    public synchronized void removeNode(String nodeName) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long hash = hash(nodeName + "#VN" + i);
            ring.remove(hash);
        }
    }

    /** Returns the physical node responsible for the given key. */
    public synchronized String getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        SortedMap<Long, String> tail = ring.tailMap(hash);
        Long targetKey = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(targetKey);
    }

    public synchronized List<Long> getRingPoints() {
        return new ArrayList<>(ring.keySet());
    }

    public synchronized Set<String> getPhysicalNodes() {
        return new HashSet<>(ring.values());
    }

    public int getVirtualNodesPerNode() {
        return virtualNodesPerNode;
    }

    /** MD5-based 32-bit hash, used for both node points and lookup keys. */
    public static long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            // Use the first 4 bytes of the digest as an unsigned 32-bit value
            return ((long) (digest[3] & 0xFF) << 24)
                 | ((long) (digest[2] & 0xFF) << 16)
                 | ((long) (digest[1] & 0xFF) << 8)
                 |  (long) (digest[0] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
