package com.normbuild.regulation.service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentParsingService {

    @Async("ragTaskExecutor")
    public CompletableFuture<String> normalizeContentAsync(String content) {
        String normalized = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("\\s+", " "))
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        return CompletableFuture.completedFuture(normalized);
    }

    @Async("ragTaskExecutor")
    public CompletableFuture<String> buildSearchSummaryAsync(String content) {
        String summary = Arrays.stream(content.split("\\."))
                .map(String::trim)
                .filter(sentence -> sentence.length() > 24)
                .limit(6)
                .reduce("", (left, right) -> left.isBlank() ? right : left + ". " + right);
        return CompletableFuture.completedFuture(summary.isBlank() ? content : summary);
    }
}
