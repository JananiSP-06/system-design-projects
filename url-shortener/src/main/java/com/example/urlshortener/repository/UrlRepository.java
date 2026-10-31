package com.example.urlshortener.repository;

import com.example.urlshortener.model.Url;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UrlRepository extends MongoRepository<Url,String> {

    Optional<Url> findByShortCode(String shortCode);

    Optional<Url> findByLongUrl(String longUrl);

}