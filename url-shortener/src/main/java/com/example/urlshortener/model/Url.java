package com.example.urlshortener.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "urls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Url {

    @Id
    private String id;

    @Indexed(unique = true)
    private String shortCode;

    @Indexed(unique = true)
    private String longUrl;
}