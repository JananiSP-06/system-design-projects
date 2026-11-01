package com.example.autocomplete.trie;

import java.util.HashMap;
import java.util.Map;

/**
 * A single node in the Trie.
 * Each node maps a character to its child node.
 * isEndOfWord marks a complete search term, and frequency stores
 * how often that term has been searched (used for Top-K ranking).
 */
public class TrieNode {

    private final Map<Character, TrieNode> children = new HashMap<>();
    private boolean isEndOfWord = false;
    private long frequency = 0;

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public boolean isEndOfWord() {
        return isEndOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        isEndOfWord = endOfWord;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }

    public TrieNode getChild(char c) {
        return children.get(c);
    }

    public TrieNode getOrCreateChild(char c) {
        return children.computeIfAbsent(c, k -> new TrieNode());
    }
}
