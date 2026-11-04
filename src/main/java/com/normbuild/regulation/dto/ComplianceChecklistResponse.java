package com.normbuild.regulation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ComplianceChecklistResponse(
        String answer,
        String riskLevel,
        List<CitedRegulationResponse> citations,
        OffsetDateTime generatedAt,
        ChecklistGenerationMode generationMode,
        ProjectAnalysisResponse projectAnalysis
) {
}
