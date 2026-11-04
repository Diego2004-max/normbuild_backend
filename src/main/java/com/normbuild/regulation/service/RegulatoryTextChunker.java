package com.normbuild.regulation.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryTextChunker {

    private static final int MAX_CHUNK_LENGTH = 6000;
    private static final int CHUNK_OVERLAP = 400;
    private static final Pattern ARTICLE_HEADING = Pattern.compile(
            "(?ium)^\\h*ART[IÍ]CULO\\h+(\\d{1,4}[A-Z]?(?:[.-]\\d+)*(?:-[A-Z])?)\\b[^\\r\\n]*");

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

    public List<RegulatoryChunk> chunkWithReferences(String text) {
        String normalized = text.strip();
        Matcher headings = ARTICLE_HEADING.matcher(normalized);
        List<ArticleBoundary> boundaries = new ArrayList<>();
        while (headings.find()) {
            boundaries.add(new ArticleBoundary(headings.start(), headings.group(1)));
        }
        if (boundaries.size() < 3) {
            List<String> sections = chunk(normalized);
            List<RegulatoryChunk> result = new ArrayList<>(sections.size());
            for (int index = 0; index < sections.size(); index++) {
                result.add(new RegulatoryChunk("Sección " + (index + 1) + " de " + sections.size(), sections.get(index)));
            }
            return List.copyOf(result);
        }

        List<RegulatoryChunk> result = new ArrayList<>();
        for (int index = 0; index < boundaries.size(); index++) {
            ArticleBoundary boundary = boundaries.get(index);
            int end = index + 1 < boundaries.size() ? boundaries.get(index + 1).start() : normalized.length();
            List<String> parts = chunk(normalized.substring(boundary.start(), end));
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                String reference = "Artículo " + boundary.number();
                if (parts.size() > 1) {
                    reference += " (parte " + (partIndex + 1) + " de " + parts.size() + ")";
                }
                result.add(new RegulatoryChunk(reference, parts.get(partIndex)));
            }
        }
        return List.copyOf(result);
    }

    public record RegulatoryChunk(String articleReference, String content) {
    }

    private record ArticleBoundary(int start, String number) {
    }
}
