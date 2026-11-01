package com.ssn.hashinglab.service;

import com.ssn.hashinglab.model.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StudentService {

    @Autowired
    private NodeManagerService nodeManagerService;

    /** TASK: "Route Student records to the appropriate MongoDB instance using the generated hash value." */
    public Map<String, Object> saveStudent(Student student) {
        String node = nodeManagerService.getRing().getNode(student.getId());
        MongoTemplate template = nodeManagerService.getTemplate(node);
        template.save(student);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("student", student);
        response.put("storedOnNode", node);
        return response;
    }

    /** TASK: "Retrieve Student records through the Spring Boot application." */
    public Map<String, Object> getStudent(String id) {
        String node = nodeManagerService.getRing().getNode(id);
        MongoTemplate template = nodeManagerService.getTemplate(node);
        Student student = template.findById(id, Student.class);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("student", student);
        response.put("retrievedFromNode", node);
        return response;
    }
}
