package com.normbuild.regulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficialSourceSearchRequest(
        @NotBlank @Size(min = 3, max = 100) String municipality
) {
}
