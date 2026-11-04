package com.normbuild.regulation.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        String message,
        List<String> details,
        OffsetDateTime occurredAt
) {
}
