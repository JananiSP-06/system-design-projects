package com.ssn.hashinglab.model;

/** Payload used when dynamically adding a new MongoDB storage node to the ring. */
public class NodeRequest {

    private String nodeName;
    private String mongoUri;

    public NodeRequest() {
    }

    public NodeRequest(String nodeName, String mongoUri) {
        this.nodeName = nodeName;
        this.mongoUri = mongoUri;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getMongoUri() {
        return mongoUri;
    }

    public void setMongoUri(String mongoUri) {
        this.mongoUri = mongoUri;
    }
}
