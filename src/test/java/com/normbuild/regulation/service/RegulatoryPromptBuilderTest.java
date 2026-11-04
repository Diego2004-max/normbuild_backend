package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import com.normbuild.regulation.repository.RegulatoryDocumentProjection;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulatoryPromptBuilderTest {

    private final RegulatoryPromptBuilder promptBuilder = new RegulatoryPromptBuilder();

    @Test
    void boundsLargeSourceExcerptsWhilePreservingTheirCitationMetadata() {
        RegulatoryDocumentProjection document = mock(RegulatoryDocumentProjection.class);
        when(document.getTitle()).thenReturn("Plan de Ordenamiento Territorial");
        when(document.getRegulationCode()).thenReturn("Acuerdo Municipal 004 de 2015");
        when(document.getArticleReference()).thenReturn("Artículo 12");
        when(document.getContent()).thenReturn("a".repeat(2000));

        String prompt = promptBuilder.buildChecklistPrompt(
                new ComplianceQueryRequest("Pasto", "Consulta residencial urbana"),
                List.of(document),
                new ProjectAnalysisResponse(200.0, 100.0, 200.0, BigDecimal.valueOf(0.5),
                        BigDecimal.ONE, BigDecimal.valueOf(100), List.of()));

        assertThat(prompt).contains("Source [1]: Plan de Ordenamiento Territorial Acuerdo Municipal 004 de 2015 Artículo 12");
        assertThat(prompt).contains("a".repeat(1600) + " [extracto]");
        assertThat(prompt).doesNotContain("a".repeat(1601));
    }
}
