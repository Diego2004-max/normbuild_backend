package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleRegulatoryGuidanceServiceTest {

    private final SimpleRegulatoryGuidanceService service = new SimpleRegulatoryGuidanceService();

    @Test
    void answersPermitQuestionWithoutDependingOnTheAiProvider() {
        var response = service.answer(
                new ComplianceQueryRequest("Pasto", "¿Puedo construir mi casa sin permisos?"),
                new ProjectAnalysisResponse(null, null, null, null, null, null, List.of()));

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().answer()).startsWith("Respuesta corta: No.")
                .contains("Decreto 1077 de 2015", "Secretaría de Planeación de Pasto");
        assertThat(response.orElseThrow().citations()).hasSize(1);
        assertThat(response.orElseThrow().generationMode()).isEqualTo(ChecklistGenerationMode.VERIFIED_GUIDANCE);
    }

    @Test
    void leavesDetailedProjectQuestionsForTheRagPipeline() {
        var response = service.answer(
                new ComplianceQueryRequest("Pasto", "Casa de dos pisos con aislamientos y altura de siete metros."),
                new ProjectAnalysisResponse(null, null, null, null, null, null, List.of()));

        assertThat(response).isEmpty();
    }
}
