package com.example.autocomplete.trie;

import com.example.autocomplete.model.SearchTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trie (prefix tree) supporting:
 *  - insert(term, frequency)  -> O(L) where L = length of the term
 *  - searchByPrefix(prefix)   -> O(P + N) where P = prefix length,
 *                                 N = number of nodes in the matched subtree
 *
 * Instead of a full linear scan over every stored term for every request,
 * the Trie walks directly to the node representing the prefix and only
 * explores the subtree under it.
 */
public class Trie {

    private final TrieNode root = new TrieNode();

    /** Insert a term with its search frequency. */
    public void insert(String term, long frequency) {
        if (term == null || term.isEmpty()) {
            return;
        }
        String normalized = term.toLowerCase();
        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            current = current.getOrCreateChild(c);
        }
        current.setEndOfWord(true);
        current.setFrequency(frequency);
    }

    /**
     * Increment the frequency of an existing term (simulates a term being
     * searched again). If the term does not exist, it is inserted with
     * frequency = 1.
     */
    public void recordSearch(String term) {
        if (term == null || term.isEmpty()) {
            return;
        }
        String normalized = term.toLowerCase();
        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            current = current.getOrCreateChild(c);
        }
        current.setEndOfWord(true);
        current.setFrequency(current.getFrequency() + 1);
    }

    /**
     * Find all terms under the given prefix, ranked by frequency (desc),
     * and return the top K.
     */
    public List<SearchTerm> searchByPrefix(String prefix, int topK) {
        List<SearchTerm> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return results;
        }
        String normalized = prefix.toLowerCase();

        // Step 1: walk down to the node representing the prefix
        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            TrieNode next = current.getChild(c);
            if (next == null) {
                // prefix doesn't exist in the Trie at all
                return results;
            }
            current = next;
        }

        // Step 2: DFS from that node to collect all complete words below it
        StringBuilder sb = new StringBuilder(normalized);
        collectWords(current, sb, results);

        // Step 3: sort by frequency descending and take top K
        Collections.sort(results);
        if (results.size() > topK) {
            return new ArrayList<>(results.subList(0, topK));
        }
        return results;
    }

    private void collectWords(TrieNode node, StringBuilder prefixSoFar, List<SearchTerm> results) {
        if (node.isEndOfWord()) {
            results.add(new SearchTerm(prefixSoFar.toString(), node.getFrequency()));
        }
        for (var entry : node.getChildren().entrySet()) {
            prefixSoFar.append(entry.getKey());
            collectWords(entry.getValue(), prefixSoFar, results);
            prefixSoFar.deleteCharAt(prefixSoFar.length() - 1);
        }
    }
}
