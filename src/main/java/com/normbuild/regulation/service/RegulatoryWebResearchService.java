package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.CitedRegulationResponse;
import com.normbuild.regulation.dto.CitationSourceType;
import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RegulatoryWebResearchService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RegulatoryWebResearchService.class);

    private static final int MAX_CITATION_EXCERPT_LENGTH = 4500;
    private final BraveRegulatoryContextProvider contextProvider;
    private final ProjectAnalysisService projectAnalysisService;
    private final RegulatoryPromptBuilder promptBuilder;
    private final RegulatoryAnswerSanitizer answerSanitizer;
    private final LlmClient llmClient;
    private final Executor ragTaskExecutor;

    public RegulatoryWebResearchService(
            BraveRegulatoryContextProvider contextProvider,
            ProjectAnalysisService projectAnalysisService,
            RegulatoryPromptBuilder promptBuilder,
            RegulatoryAnswerSanitizer answerSanitizer,
            LlmClient llmClient,
            @Qualifier("ragTaskExecutor") Executor ragTaskExecutor) {
        this.contextProvider = contextProvider;
        this.projectAnalysisService = projectAnalysisService;
        this.promptBuilder = promptBuilder;
        this.answerSanitizer = answerSanitizer;
        this.llmClient = llmClient;
        this.ragTaskExecutor = ragTaskExecutor;
    }

    public CompletableFuture<ComplianceChecklistResponse> research(ComplianceQueryRequest request) {
        return research(request, response -> { });
    }

    public CompletableFuture<ComplianceChecklistResponse> research(
            ComplianceQueryRequest request, Consumer<ComplianceChecklistResponse> onProgress) {
        return research(request, List.of(), onProgress);
    }

    public CompletableFuture<ComplianceChecklistResponse> research(
            ComplianceQueryRequest request, List<CitedRegulationResponse> indexedCitations,
            Consumer<ComplianceChecklistResponse> onProgress) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.info("Web research started: requestId={}", requestId);
        ProjectAnalysisResponse analysis = projectAnalysisService.analyze(request.projectDescription());
        return contextProvider.search(request.jurisdiction())
                .thenApplyAsync(documents -> {
                    List<CitedRegulationResponse> liveCitations = documents.stream()
                            .map(this::toCitation)
                            .toList();
                    List<CitedRegulationResponse> citations = new java.util.ArrayList<>(indexedCitations);
                    liveCitations.stream().filter(live -> indexedCitations.stream()
                            .noneMatch(indexed -> indexed.sourceUrl().equals(live.sourceUrl())))
                            .limit(indexedCitations.isEmpty() ? 6 : 2).forEach(citations::add);
                    LOGGER.info("Web research sources retrieved: requestId={}, indexed={}, live={}",
                            requestId, indexedCitations.size(), liveCitations.size());
                    if (citations.isEmpty()) {
                        return new ComplianceChecklistResponse(
                                "No encontré extractos suficientes en fuentes oficiales para esta ciudad. Verifica el nombre del municipio o consulta su Secretaría de Planeación.",
                                "Por verificar", List.of(), OffsetDateTime.now(), ChecklistGenerationMode.NO_CONTEXT, analysis);
                    }
                    onProgress.accept(new ComplianceChecklistResponse("", "Por verificar", citations,
                            OffsetDateTime.now(), ChecklistGenerationMode.PENDING, analysis));
                    String prompt = promptBuilder.buildWebResearchPrompt(request, citations, analysis);
                    try {
                        String answer = answerSanitizer.sanitize(
                                llmClient.generateChecklist(prompt), analysis, citations, request.projectDescription());
                        return new ComplianceChecklistResponse(answer, "Por verificar", citations,
                                OffsetDateTime.now(), ChecklistGenerationMode.WEB_RESEARCH, analysis);
                    } catch (AiProviderException exception) {
                        Throwable cause = exception;
                        while (cause.getCause() != null && cause.getCause() != cause) {
                            cause = cause.getCause();
                        }
                        Integer status = cause instanceof org.springframework.web.reactive.function.client.WebClientResponseException http
                                ? http.getStatusCode().value() : null;
                        LOGGER.warn("Web research generation failed: requestId={}, errorType={}, httpStatus={}, reason={}",
                                requestId, cause.getClass().getSimpleName(), status, exception.getMessage());
                        return new ComplianceChecklistResponse(
                                "La búsqueda encontró extractos en páginas oficiales, pero el modelo local no pudo redactar la orientación. Revisa las fuentes y vuelve a intentarlo.",
                                "Por verificar", citations, OffsetDateTime.now(), ChecklistGenerationMode.UNAVAILABLE, analysis);
                    }
                }, ragTaskExecutor);
    }

    private CitedRegulationResponse toCitation(OfficialWebContextDocument document) {
        String excerpt = String.join("\n", document.snippets());
        if (excerpt.length() > MAX_CITATION_EXCERPT_LENGTH) {
            excerpt = excerpt.substring(0, MAX_CITATION_EXCERPT_LENGTH);
        }
        UUID id = UUID.nameUUIDFromBytes(document.url().getBytes(StandardCharsets.UTF_8));
        return new CitedRegulationResponse(id, document.title(), "Investigación web · norma por verificar",
                "Extractos de búsqueda; artículo no verificado", document.url(), 0.0, excerpt, CitationSourceType.LIVE_WEB);
    }
}
