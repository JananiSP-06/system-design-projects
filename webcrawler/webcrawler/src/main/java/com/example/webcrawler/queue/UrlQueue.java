package com.example.webcrawler.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class UrlQueue {

    private final BlockingQueue<String> queue =
            new LinkedBlockingQueue<>();

    public void addUrl(String url) {

        queue.offer(url);
    }

    public String getUrl() {

        return queue.poll();
    }

    public int getSize() {

        return queue.size();
    }

    public boolean isEmpty() {

        return queue.isEmpty();
    }
    public void clear() {
        queue.clear();
    }
}