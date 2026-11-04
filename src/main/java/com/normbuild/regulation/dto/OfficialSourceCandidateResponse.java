package com.normbuild.regulation.dto;

public record OfficialSourceCandidateResponse(
        String title,
        String url,
        String summary,
        String issuingHost,
        String reviewStatus
) {
}
