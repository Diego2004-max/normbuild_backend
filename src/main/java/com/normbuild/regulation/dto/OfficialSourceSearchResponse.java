package com.normbuild.regulation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OfficialSourceSearchResponse(
        String municipality,
        OffsetDateTime searchedAt,
        List<OfficialSourceCandidateResponse> candidates
) {
}
