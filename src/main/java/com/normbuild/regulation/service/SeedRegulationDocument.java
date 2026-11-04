package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.DocumentIngestionRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record SeedRegulationDocument(
        UUID id,
        String title,
        String jurisdiction,
        String regulationCode,
        String articleReference,
        String content
) {

    public static SeedRegulationDocument from(String title, String jurisdiction, String regulationCode, String articleReference, String content) {
        String key = title + "|" + jurisdiction + "|" + regulationCode + "|" + articleReference;
        return new SeedRegulationDocument(
                UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                title,
                jurisdiction,
                regulationCode,
                articleReference,
                content
        );
    }

    public DocumentIngestionRequest toIngestionRequest() {
        return new DocumentIngestionRequest(title, jurisdiction, regulationCode, articleReference, content);
    }
}
