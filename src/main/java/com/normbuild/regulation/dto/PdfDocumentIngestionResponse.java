package com.normbuild.regulation.dto;

import java.util.UUID;

public record PdfDocumentIngestionResponse(
        UUID sourceDocumentId,
        int indexedChunks,
        String status
) {
}
