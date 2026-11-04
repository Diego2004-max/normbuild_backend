package com.normbuild.regulation.dto;

import java.time.OffsetDateTime;

public record JurisdictionCoverageResponse(
        String jurisdiction,
        long indexedSections,
        long officialSources,
        OffsetDateTime lastIndexedAt,
        String status
) {
}
