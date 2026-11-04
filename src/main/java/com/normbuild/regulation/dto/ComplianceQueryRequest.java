package com.normbuild.regulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplianceQueryRequest(
        @NotBlank @Size(max = 120) String jurisdiction,
        @NotBlank @Size(min = 12, max = 1600) String projectDescription
) {
}
