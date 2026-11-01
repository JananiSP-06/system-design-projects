package com.ssn.hashinglab.controller;

import com.ssn.hashinglab.model.NodeRequest;
import com.ssn.hashinglab.service.NodeManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    @Autowired
    private NodeManagerService nodeManagerService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNodes() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeNodes", nodeManagerService.getActiveNodes());
        response.put("virtualNodesPerNode", nodeManagerService.getRing().getVirtualNodesPerNode());
        response.put("totalRingPoints", nodeManagerService.getRing().getRingPoints().size());
        return ResponseEntity.ok(response);
    }

    /** TASK: "Add a new MongoDB storage node and update the hash ring." */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addNode(@RequestBody NodeRequest request) {
        Map<String, Object> result = nodeManagerService.addNodeDynamically(request.getNodeName(), request.getMongoUri());
        return ResponseEntity.ok(result);
    }

    /** TASK: "Remove an existing MongoDB storage node from the hash ring." */
    @DeleteMapping("/{nodeName}")
    public ResponseEntity<Map<String, Object>> removeNode(@PathVariable String nodeName) {
        Map<String, Object> result = nodeManagerService.removeNodeDynamically(nodeName);
        return ResponseEntity.ok(result);
    }
}
