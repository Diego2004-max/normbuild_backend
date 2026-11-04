package com.normbuild.regulation.service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingFormatter {

    public String toVectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(value -> String.format(java.util.Locale.US, "%.8f", value))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
