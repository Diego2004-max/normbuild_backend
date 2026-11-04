package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import com.normbuild.regulation.dto.CitedRegulationResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegulatoryAnswerSanitizerTest {

    private final RegulatoryAnswerSanitizer sanitizer = new RegulatoryAnswerSanitizer();

    @Test
    void keepsCalculatedProposalRatiosAndRemovesUnverifiedLegalLimits() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(
                200.0, 100.0, 200.0, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.valueOf(100), List.of());
        List<CitedRegulationResponse> citations = List.of(
                new CitedRegulationResponse(UUID.randomUUID(), "POT Pasto", "Acuerdo 004 de 2015",
                        "Artículo 250 (parte 1 de 4)", "https://pasto.gov.co/norma", 0.9, "Edificabilidades y índices."),
                new CitedRegulationResponse(UUID.randomUUID(), "POT Pasto", "Acuerdo 004 de 2015",
                        "Artículo 267", "https://pasto.gov.co/norma", 0.8, "Aislamientos para edificación aislada."));

        String answer = sanitizer.sanitize(
                "**La casa requiere revisión.**\n1. El índice máximo permitido es 0,50 para edificaciones aisladas [1].\nRevisa la ficha normativa.",
                analysis, citations);

        assertThat(answer).contains("100 / 200 m² = 0,50 (50%)", "200 / 200 m² = 1,00 (100%)");
        assertThat(answer).contains("La casa requiere revisión", "Revisa la ficha normativa");
        assertThat(answer).doesNotContain("máximo permitido es 0,50");
        assertThat(answer).doesNotContain("*");
        assertThat(answer).contains("tratamiento urbanístico, uso permitido y tipología");
        assertThat(answer).contains("tipo de edificabilidad y el código morfológico de altura", "[1]");
        assertThat(answer).contains("Si la edificación es aislada", "[2]");
    }
}
