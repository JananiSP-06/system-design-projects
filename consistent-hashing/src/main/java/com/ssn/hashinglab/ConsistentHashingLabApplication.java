package com.ssn.hashinglab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * UCS3513 - System Design Laboratory
 * Lab Exercise 4: Distributed Data Routing using Consistent Hashing
 *
 * NOTE: We deliberately exclude Spring's default single-datasource Mongo
 * auto-configuration because this application talks to THREE independent
 * MongoDB instances at once. Connections are created manually, per node,
 * inside NodeManagerService.
 */
@SpringBootApplication(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@ConfigurationPropertiesScan
public class ConsistentHashingLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsistentHashingLabApplication.class, args);
    }
}
