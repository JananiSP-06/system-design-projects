package com.example.autocomplete.model;

import java.io.Serializable;

/**
 * Represents a single search term and how often it has been searched.
 * Frequency is used to rank suggestions (Top-K).
 */
public class SearchTerm implements Serializable, Comparable<SearchTerm> {

    private String term;
    private long frequency;

    public SearchTerm() {
    }

    public SearchTerm(String term, long frequency) {
        this.term = term;
        this.frequency = frequency;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }

    // Higher frequency should come first -> reverse natural order
    @Override
    public int compareTo(SearchTerm other) {
        return Long.compare(other.frequency, this.frequency);
    }

    @Override
    public String toString() {
        return "SearchTerm{term='" + term + "', frequency=" + frequency + '}';
    }
}
