package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.CitationSourceType;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RegulatoryWebResearchServiceTest {

    private final BraveRegulatoryContextProvider contextProvider = mock(BraveRegulatoryContextProvider.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final RegulatoryWebResearchService service = new RegulatoryWebResearchService(
            contextProvider, new ProjectAnalysisService(new ProjectFactExtractor()),
            new RegulatoryPromptBuilder(), new RegulatoryAnswerSanitizer(), llmClient, Runnable::run);

    @Test
    void keepsIndexedCitationFirstAndDeduplicatesMatchingWebUrl() {
        var request = new ComplianceQueryRequest("Neiva", "Casa de dos pisos en lote de 180 m2");
        String indexedUrl = "https://planeacion.neiva.gov.co/pot";
        var indexed = new com.normbuild.regulation.dto.CitedRegulationResponse(
                UUID.randomUUID(), "POT indexado", "Acuerdo local", "Artículo 10", indexedUrl,
                0.8, "Texto oficial indexado.");
        when(contextProvider.search("Neiva")).thenReturn(CompletableFuture.completedFuture(List.of(
                new OfficialWebContextDocument("Duplicado", indexedUrl, "planeacion.neiva.gov.co", List.of("Extracto repetido")),
                new OfficialWebContextDocument("Fuente reciente", "https://neiva.gov.co/actualizacion",
                        "neiva.gov.co", List.of("Actualización por verificar.")))));
        when(llmClient.generateChecklist(anyString())).thenReturn("Verifica la ficha normativa [1].");

        var response = service.research(request, List.of(indexed), progress -> { }).join();

        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0).sourceType()).isEqualTo(CitationSourceType.INDEXED_VECTOR);
        assertThat(response.citations().get(1).sourceType()).isEqualTo(CitationSourceType.LIVE_WEB);
        assertThat(response.citations().get(1).sourceUrl()).isEqualTo("https://neiva.gov.co/actualizacion");
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmClient).generateChecklist(prompt.capture());
        assertThat(prompt.getValue()).contains("POT indexado", "Fuente reciente");
    }

    @Test
    void groundsLocalModelAnswerInLiveGovernmentPageExtracts() {
        var request = new ComplianceQueryRequest("Neiva", "Casa de dos pisos en lote de 180 m2");
        when(contextProvider.search("Neiva")).thenReturn(CompletableFuture.completedFuture(List.of(
                new OfficialWebContextDocument("POT de Neiva", "https://planeacion.neiva.gov.co/pot",
                        "planeacion.neiva.gov.co", List.of("La página oficial describe el Plan de Ordenamiento Territorial de Neiva.")))));
        when(llmClient.generateChecklist(anyString())).thenReturn("Confirma la ficha normativa y uso del predio [1].");

        List<com.normbuild.regulation.dto.ComplianceChecklistResponse> progress = new ArrayList<>();
        var response = service.research(request, progress::add).join();

        assertThat(response.generationMode()).isEqualTo(ChecklistGenerationMode.WEB_RESEARCH);
        assertThat(response.riskLevel()).isEqualTo("Por verificar");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.sourceType()).isEqualTo(CitationSourceType.LIVE_WEB);
            assertThat(citation.sourceUrl()).isEqualTo("https://planeacion.neiva.gov.co/pot");
        });
        assertThat(response.answer()).contains("Confirma la ficha normativa", "Riesgo: Por verificar");
        assertThat(progress).singleElement().satisfies(item -> {
            assertThat(item.generationMode()).isEqualTo(ChecklistGenerationMode.PENDING);
            assertThat(item.citations()).hasSize(1);
        });
        verify(llmClient).generateChecklist(org.mockito.ArgumentMatchers.contains("Casa de dos pisos"));
    }

    @Test
    void doesNotGenerateAnAnswerWhenSearchReturnsNoOfficialContent() {
        when(contextProvider.search("Neiva")).thenReturn(CompletableFuture.completedFuture(List.of()));

        var response = service.research(new ComplianceQueryRequest("Neiva", "Casa de dos pisos" )).join();

        assertThat(response.generationMode()).isEqualTo(ChecklistGenerationMode.NO_CONTEXT);
        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).contains("No encontré extractos");
    }
}
