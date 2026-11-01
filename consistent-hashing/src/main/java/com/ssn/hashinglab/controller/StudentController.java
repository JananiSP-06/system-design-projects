package com.ssn.hashinglab.controller;

import com.ssn.hashinglab.model.Student;
import com.ssn.hashinglab.service.NodeManagerService;
import com.ssn.hashinglab.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private NodeManagerService nodeManagerService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> addStudent(@RequestBody Student student) {
        return ResponseEntity.ok(studentService.saveStudent(student));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getStudent(@PathVariable String id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }

    /** TASK: "Compare the data distribution before and after adding or removing storage nodes." */
    @GetMapping("/distribution")
    public ResponseEntity<Map<String, Long>> getDistribution() {
        return ResponseEntity.ok(nodeManagerService.getDistribution());
    }
}
