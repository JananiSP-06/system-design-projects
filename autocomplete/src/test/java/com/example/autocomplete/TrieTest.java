package com.example.autocomplete;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.trie.Trie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrieTest {

    private Trie trie;

    @BeforeEach
    void setUp() {
        trie = new Trie();
        trie.insert("apple", 120);
        trie.insert("application", 85);
        trie.insert("appointment", 40);
        trie.insert("app store", 60);
        trie.insert("apricot", 15);
    }

    @Test
    void returnsSuggestionsForKnownPrefix() {
        List<SearchTerm> results = trie.searchByPrefix("app", 10);
        assertEquals(4, results.size()); // apple, application, appointment, app store
    }

    @Test
    void ranksByFrequencyDescending() {
        List<SearchTerm> results = trie.searchByPrefix("app", 10);
        assertEquals("apple", results.get(0).getTerm()); // highest frequency = 120
    }

    @Test
    void respectsTopKLimit() {
        List<SearchTerm> results = trie.searchByPrefix("app", 2);
        assertEquals(2, results.size());
    }

    @Test
    void returnsEmptyForUnknownPrefix() {
        List<SearchTerm> results = trie.searchByPrefix("xyz", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void recordSearchIncrementsFrequency() {
        trie.recordSearch("apple");
        List<SearchTerm> results = trie.searchByPrefix("apple", 1);
        assertEquals(121, results.get(0).getFrequency());
    }
}
