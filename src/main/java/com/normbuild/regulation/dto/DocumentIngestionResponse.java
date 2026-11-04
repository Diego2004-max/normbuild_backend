package com.normbuild.regulation.dto;

import java.util.UUID;

public record DocumentIngestionResponse(
        UUID documentId,
        String status
) {
}
