package com.normbuild.regulation.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryTextChunker {

    private static final int MAX_CHUNK_LENGTH = 6000;
    private static final int CHUNK_OVERLAP = 400;

    public List<String> chunk(String text) {
        String normalized = text.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, normalized.length());
            if (end < normalized.length()) {
                int paragraphBreak = normalized.lastIndexOf("\n\n", end);
                if (paragraphBreak >= start + MAX_CHUNK_LENGTH / 2) {
                    end = paragraphBreak;
                } else {
                    int wordBreak = normalized.lastIndexOf(' ', end);
                    if (wordBreak >= start + MAX_CHUNK_LENGTH / 2) {
                        end = wordBreak;
                    }
                }
            }
            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return List.copyOf(chunks);
    }
}
