package com.ssn.hashinglab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe binding for the "hashring" section of application.yml.
 *
 * Using @ConfigurationProperties instead of scattered @Value(#{...}) fields
 * because Spring Boot's YAML loader FLATTENS nested maps (e.g.
 * hashring.node-uris.node1, hashring.node-uris.node2, ...) rather than
 * keeping "hashring.node-uris" as a single resolvable placeholder.
 * @ConfigurationProperties knows how to reassemble those flattened keys
 * back into a Map<String, String> / List<String>; @Value + SpEL does not.
 */
@ConfigurationProperties(prefix = "hashring")
public class HashRingProperties {

    /** Comma-separated in YAML ("node1,node2,node3") -> bound as a List<String>. */
    private List<String> initialNodes;

    /** Logical node name -> MongoDB connection URI. */
    private Map<String, String> nodeUris = new LinkedHashMap<>();

    private int virtualNodes = 150;

    public List<String> getInitialNodes() {
        return initialNodes;
    }

    public void setInitialNodes(List<String> initialNodes) {
        this.initialNodes = initialNodes;
    }

    public Map<String, String> getNodeUris() {
        return nodeUris;
    }

    public void setNodeUris(Map<String, String> nodeUris) {
        this.nodeUris = nodeUris;
    }

    public int getVirtualNodes() {
        return virtualNodes;
    }

    public void setVirtualNodes(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }
}
