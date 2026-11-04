package com.normbuild.regulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentIngestionRequest(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 120) String jurisdiction,
        @NotBlank @Size(max = 80) String regulationCode,
        @NotBlank @Size(max = 120) String articleReference,
        @NotBlank @Size(max = 2000) String sourceUrl,
        @NotBlank @Size(min = 40, max = 20000) String content
) {
}
