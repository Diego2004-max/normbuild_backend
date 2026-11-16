package com.normbuild.account;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QueryHistoryItem(
        UUID id, String jurisdiction, String projectDescription,
        String answer, String riskLevel, String generationMode, OffsetDateTime createdAt
) {
}
