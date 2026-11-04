package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;

import com.normbuild.config.NormBuildAiProperties;
import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.DocumentIngestionRequest;
import com.normbuild.regulation.repository.RegulatoryDocumentJdbcRepository;
import com.normbuild.regulation.repository.RegulatoryDocumentProjection;
import com.normbuild.regulation.repository.RegulatoryDocumentRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegulatoryRagServiceTest {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final RegulatoryDocumentRepository repository = mock(RegulatoryDocumentRepository.class);
    private final RegulatoryDocumentJdbcRepository jdbcRepository = mock(RegulatoryDocumentJdbcRepository.class);
    private final DocumentParsingService parsingService = mock(DocumentParsingService.class);
    private final ComplianceQueryRequest request = new ComplianceQueryRequest(
            "Bogotá D.C.", "Casa de dos pisos con altura total de 7.5 metros en un lote de 180 m².");
    private RegulatoryRagService service;

    @BeforeEach
    void setUp() {
        NormBuildAiProperties properties = new NormBuildAiProperties(
                768, "http://localhost:11434/api/generate", "http://localhost:11434/api/embeddings",
                "llama3.1", "nomic-embed-text", 0.35, 5, 30, 120, 4096, 512, "10m");
        service = new RegulatoryRagService(properties, embeddingClient, llmClient,
                new EmbeddingFormatter(), new RegulatoryPromptBuilder(),
                parsingService, repository,
                jdbcRepository, Runnable::run,
                new ProjectAnalysisService(new ProjectFactExtractor()),
                new RegulatoryAnswerSanitizer());
        when(embeddingClient.createEmbedding(anyString())).thenReturn(List.of(0.2, 0.8));
        RegulatoryDocumentProjection document = source();
        when(repository.findNearestRegulations(anyString(), anyString(), anyDouble(), anyBoolean(), anyInt()))
                .thenReturn(List.of(document));
    }

    @Test
    void enrichesResidentialSearchWithoutChangingTheCitizenQuestion() {
        when(llmClient.generateChecklist(anyString())).thenReturn("Revisión terminada.");

        service.answerComplianceQuestion(request).join();

        ArgumentCaptor<String> retrievalQuery = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).createEmbedding(retrievalQuery.capture());
        assertThat(retrievalQuery.getValue()).contains("vivienda residencial", "edificabilidad para predio individual",
                "equipamientos");
        verify(repository).findNearestRegulations(anyString(), anyString(), anyDouble(), eq(true), eq(2));
    }

    @Test
    void reportsAiUnavailableWithoutInventingGuidanceAndKeepsCitations() {
        when(llmClient.generateChecklist(anyString())).thenThrow(new AiProviderException("Modelo no disponible."));

        ComplianceChecklistResponse response = service.answerComplianceQuestion(request).join();

        assertThat(response.generationMode()).isEqualTo(ChecklistGenerationMode.UNAVAILABLE);
        assertThat(response.answer()).contains("No se generó un checklist", "ni se evaluó el cumplimiento");
        assertThat(response.answer()).doesNotContain("NSR-10", "licencia de construcción", "retiros", "índice de ocupación");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().sourceUrl()).isEqualTo("https://bogotajuridica.gov.co/norma");
        assertThat(response.riskLevel()).isEqualTo("Por verificar");
    }
    @Test
    void doesNotTranslateHighSimilarityIntoLowComplianceRisk() {
        when(llmClient.generateChecklist(anyString())).thenReturn("Verifica la norma específica del predio.");

        ComplianceChecklistResponse response = service.answerComplianceQuestion(request).join();

        assertThat(response.generationMode()).isEqualTo(ChecklistGenerationMode.GENERATIVE);
        assertThat(response.citations().getFirst().similarity()).isEqualTo(0.99);
        assertThat(response.riskLevel()).isEqualTo("Por verificar");
        verify(llmClient).generateChecklist(anyString());
    }

    @Test
    void doesNotGenerateWithoutRetrievedSources() {
        when(repository.findNearestRegulations(anyString(), anyString(), anyDouble(), anyBoolean(), anyInt())).thenReturn(List.of());

        ComplianceChecklistResponse response = service.answerComplianceQuestion(request).join();

        assertThat(response.generationMode()).isEqualTo(ChecklistGenerationMode.NO_CONTEXT);
        assertThat(response.citations()).isEmpty();
        verify(llmClient, never()).generateChecklist(anyString());
    }

    @Test
    void propagatesEmbeddingFailureInsteadOfFabricatingSources() {
        when(embeddingClient.createEmbedding(anyString())).thenThrow(new AiProviderException("Vector no disponible."));

        assertThatThrownBy(() -> service.answerComplianceQuestion(request).join())
                .isInstanceOf(CompletionException.class).hasCauseInstanceOf(AiProviderException.class);
        verify(repository, never()).findNearestRegulations(anyString(), anyString(), anyDouble(), anyBoolean(), anyInt());
        verify(llmClient, never()).generateChecklist(anyString());
    }

    private RegulatoryDocumentProjection source() {
        RegulatoryDocumentProjection source = mock(RegulatoryDocumentProjection.class);
        when(source.getId()).thenReturn(UUID.fromString("b6180b28-0944-4e94-a612-dbd0b56c04db"));
        when(source.getTitle()).thenReturn("Documento de prueba");
        when(source.getRegulationCode()).thenReturn("Reglamento de prueba");
        when(source.getArticleReference()).thenReturn("Referencia de prueba");
        when(source.getSourceUrl()).thenReturn("https://bogotajuridica.gov.co/norma");
        when(source.getContent()).thenReturn("Confirma la norma específica del predio.");
        when(source.getSimilarity()).thenReturn(0.99);
        return source;
    }

    @Test
    void emitsComputedDataAndCompleteSourcesBeforeModelGeneration() {
        List<ComplianceChecklistResponse> progress = new ArrayList<>();
        when(llmClient.generateChecklist(anyString())).thenAnswer(invocation -> {
            assertThat(progress).hasSize(2);
            assertThat(progress.getFirst().generationMode()).isEqualTo(ChecklistGenerationMode.PENDING);
            assertThat(progress.getFirst().projectAnalysis().lotAreaSquareMeters()).isEqualTo(180.0);
            assertThat(progress.get(1).citations()).hasSize(1);
            return "Revisión terminada.";
        });

        service.answerComplianceQuestion(request, progress::add).join();
    }

    @Test
    void retainsEntireSourceTextAndReusesCompletedResponses() {
        String content = "Esta fuente contiene oraciones completas. ".repeat(30);
        RegulatoryDocumentProjection document = source();
        when(document.getContent()).thenReturn(content);
        when(repository.findNearestRegulations(anyString(), anyString(), anyDouble(), anyBoolean(), anyInt())).thenReturn(List.of(document));
        when(llmClient.generateChecklist(anyString())).thenReturn("Revisión terminada.");

        ComplianceChecklistResponse first = service.answerComplianceQuestion(request).join();
        ComplianceChecklistResponse second = service.answerComplianceQuestion(request).join();

        assertThat(first.citations().getFirst().excerpt()).isEqualTo(content);
        assertThat(second).isSameAs(first);
        verify(llmClient, times(1)).generateChecklist(anyString());
        verify(embeddingClient, times(1)).createEmbedding(anyString());
    }

    @Test
    void retriesGenerationAfterAnUnavailableAiResponse() {
        when(llmClient.generateChecklist(anyString())).thenThrow(new AiProviderException("Modelo no disponible."));

        service.answerComplianceQuestion(request).join();
        service.answerComplianceQuestion(request).join();

        verify(llmClient, times(2)).generateChecklist(anyString());
    }

    @Test
    void rejectsNonGovernmentSourceUrlsBeforeStartingIngestion() {
        DocumentIngestionRequest untrustedSource = new DocumentIngestionRequest(
                "Documento nuevo", "Bogotá D.C.", "Reglamento de prueba", "Referencia de prueba",
                "https://example.com/norma", "Contenido del documento nuevo suficientemente largo para indexar.");

        assertThatThrownBy(() -> service.ingestDocument(untrustedSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dominio oficial .gov.co");
        verify(parsingService, never()).normalizeContentAsync(anyString());
    }

    @Test
    void invalidatesCompletedResponsesWhenKnowledgeIsUpdated() {
        when(llmClient.generateChecklist(anyString())).thenReturn("Revisión terminada.");
        when(jdbcRepository.insertDocument(any(UUID.class), any(DocumentIngestionRequest.class), anyString())).thenReturn(1);
        when(parsingService.normalizeContentAsync(anyString())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("Contenido normalizado."));
        when(parsingService.buildSearchSummaryAsync(anyString())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("Resumen de búsqueda."));
        service.answerComplianceQuestion(request).join();

        service.ingestDocument(new DocumentIngestionRequest("Documento nuevo", "Bogotá D.C.",
                "Reglamento de prueba", "Referencia de prueba", "https://bogota.gov.co/normativa/prueba",
                "Contenido del documento nuevo suficientemente largo para indexar.")).join();
        service.answerComplianceQuestion(request).join();

        verify(llmClient, times(2)).generateChecklist(anyString());
    }
}
