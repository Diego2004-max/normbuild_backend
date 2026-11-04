package com.normbuild.regulation.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjectAnalysisResponse(
        Double lotAreaSquareMeters,
        Double occupiedAreaSquareMeters,
        Double totalBuiltAreaSquareMeters,
        BigDecimal occupancyIndex,
        BigDecimal constructionIndex,
        BigDecimal unoccupiedAreaSquareMeters,
        List<String> observations
) {
}
