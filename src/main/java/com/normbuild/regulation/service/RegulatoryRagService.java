package com.normbuild.regulation.service;

import com.normbuild.config.NormBuildAiProperties;
import com.normbuild.regulation.dto.CitedRegulationResponse;
import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.DocumentIngestionRequest;
import com.normbuild.regulation.dto.DocumentIngestionResponse;
import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import com.normbuild.regulation.repository.RegulatoryDocumentJdbcRepository;
import com.normbuild.regulation.repository.RegulatoryDocumentProjection;
import com.normbuild.regulation.repository.RegulatoryDocumentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RegulatoryRagService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegulatoryRagService.class);

    private final NormBuildAiProperties properties;
    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;
    private final EmbeddingFormatter embeddingFormatter;
    private final RegulatoryPromptBuilder promptBuilder;
    private final DocumentParsingService parsingService;
    private final RegulatoryDocumentRepository documentRepository;
    private final RegulatoryDocumentJdbcRepository jdbcRepository;
    private final Executor ragTaskExecutor;
    private final ProjectAnalysisService projectAnalysisService;
    private final RegulatoryAnswerSanitizer answerSanitizer;
    private final AtomicLong knowledgeVersion = new AtomicLong();
    private final Cache<QueryCacheKey, ComplianceChecklistResponse> responseCache = Caffeine.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofMinutes(5)).build();

    public RegulatoryRagService(
            NormBuildAiProperties properties,
            EmbeddingClient embeddingClient,
            LlmClient llmClient,
            EmbeddingFormatter embeddingFormatter,
            RegulatoryPromptBuilder promptBuilder,
            DocumentParsingService parsingService,
            RegulatoryDocumentRepository documentRepository,
            RegulatoryDocumentJdbcRepository jdbcRepository,
            @Qualifier("ragTaskExecutor") Executor ragTaskExecutor,
            ProjectAnalysisService projectAnalysisService,
            RegulatoryAnswerSanitizer answerSanitizer
    ) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.llmClient = llmClient;
        this.embeddingFormatter = embeddingFormatter;
        this.promptBuilder = promptBuilder;
        this.parsingService = parsingService;
        this.documentRepository = documentRepository;
        this.jdbcRepository = jdbcRepository;
        this.ragTaskExecutor = ragTaskExecutor;
        this.projectAnalysisService = projectAnalysisService;
        this.answerSanitizer = answerSanitizer;
    }

    public CompletableFuture<ComplianceChecklistResponse> answerComplianceQuestion(ComplianceQueryRequest request) {
        return answerComplianceQuestion(request, progress -> { });
    }

    public CompletableFuture<ComplianceChecklistResponse> answerComplianceQuestion(
            ComplianceQueryRequest request, Consumer<ComplianceChecklistResponse> onProgress) {
        QueryCacheKey cacheKey = new QueryCacheKey(request, knowledgeVersion.get());
        ComplianceChecklistResponse cached = responseCache.getIfPresent(cacheKey);
        if (cached != null) {
            LOGGER.info("Returning cached completed checklist");
            return CompletableFuture.completedFuture(cached);
        }
        ProjectAnalysisResponse analysis = projectAnalysisService.analyze(request.projectDescription());
        onProgress.accept(pendingResponse(analysis, List.of()));
        boolean prioritizeBuildingRules = isBuildingRulesQuery(request.projectDescription());
        String retrievalQuery = buildRetrievalQuery(request.projectDescription(), prioritizeBuildingRules);
        int contextLimit = prioritizeBuildingRules ? Math.min(2, properties.maxContextResults()) : properties.maxContextResults();
        CompletableFuture<List<Double>> embeddingFuture = CompletableFuture.supplyAsync(
                () -> embeddingClient.createEmbedding(retrievalQuery),
                ragTaskExecutor
        );
        CompletableFuture<List<RegulatoryDocumentProjection>> contextFuture = embeddingFuture.thenApplyAsync(embedding -> {
            String vectorLiteral = embeddingFormatter.toVectorLiteral(embedding);
            return documentRepository.findNearestRegulations(
                    JurisdictionNormalizer.canonicalize(request.jurisdiction()),
                    vectorLiteral,
                    properties.similarityThreshold(),
                    prioritizeBuildingRules,
                    contextLimit
            );
        }, ragTaskExecutor);
        return contextFuture.thenApplyAsync(context -> {
            if (context.isEmpty()) {
                return new ComplianceChecklistResponse(
                        "No encontré normativa oficial suficiente para responder con seguridad. Carga documentos oficiales de la jurisdicción indicada antes de emitir un concepto.",
                        "Por verificar",
                        List.of(),
                        OffsetDateTime.now(),
                        ChecklistGenerationMode.NO_CONTEXT,
                        analysis
                );
            }
            onProgress.accept(pendingResponse(analysis, mapCitations(context)));
            String prompt = promptBuilder.buildChecklistPrompt(request, context, analysis);
            try {
                String answer = answerSanitizer.sanitize(llmClient.generateChecklist(prompt), analysis, mapCitations(context));
                LOGGER.info("Checklist completed: mode=GENERATIVE, sources={}", context.size());
                ComplianceChecklistResponse response = new ComplianceChecklistResponse(answer, "Por verificar",
                        mapCitations(context), OffsetDateTime.now(), ChecklistGenerationMode.GENERATIVE, analysis);
                responseCache.put(cacheKey, response);
                return response;
            } catch (AiProviderException exception) {
                LOGGER.warn("LLM generation unavailable; returning source references without a generated checklist", exception);
                return new ComplianceChecklistResponse(
                        "El modelo local no pudo completar la respuesta. No se generó un checklist ni se evaluó el cumplimiento. Las fuentes oficiales recuperadas se conservan como referencia; verifica el servicio de IA e inténtalo nuevamente.",
                        "Por verificar",
                        mapCitations(context),
                        OffsetDateTime.now(),
                        ChecklistGenerationMode.UNAVAILABLE,
                        analysis
                );
            }
        }, ragTaskExecutor);
    }

    @Transactional
    public CompletableFuture<DocumentIngestionResponse> ingestDocument(DocumentIngestionRequest request) {
        validateOfficialSource(request.sourceUrl());
        String jurisdiction = JurisdictionNormalizer.canonicalize(request.jurisdiction());
        CompletableFuture<String> normalizedFuture = parsingService.normalizeContentAsync(request.content());
        return normalizedFuture.thenApplyAsync(normalized -> {
            List<Double> embedding = embeddingClient.createEmbedding(normalized);
            UUID documentId = UUID.nameUUIDFromBytes((jurisdiction.toLowerCase(java.util.Locale.ROOT)
                    + "|" + request.sourceUrl() + "|" + request.articleReference().strip() + "|" + normalized)
                    .getBytes(StandardCharsets.UTF_8));
            DocumentIngestionRequest normalizedRequest = new DocumentIngestionRequest(
                    request.title(),
                    jurisdiction,
                    request.regulationCode(),
                    request.articleReference(),
                    request.sourceUrl(),
                    normalized
            );
            int inserted = jdbcRepository.insertDocument(documentId, normalizedRequest, embeddingFormatter.toVectorLiteral(embedding));
            if (inserted > 0) {
                knowledgeVersion.incrementAndGet();
                responseCache.invalidateAll();
            }
            return new DocumentIngestionResponse(documentId, inserted > 0 ? "INDEXED" : "ALREADY_INDEXED");
        }, ragTaskExecutor);
    }

    private List<CitedRegulationResponse> mapCitations(List<RegulatoryDocumentProjection> documents) {
        return documents.stream()
                .map(document -> new CitedRegulationResponse(
                        document.getId(),
                        document.getTitle(),
                        document.getRegulationCode(),
                        document.getArticleReference(),
                        document.getSourceUrl(),
                        document.getSimilarity(),
                        document.getContent()
                ))
                .toList();
    }

    private boolean isBuildingRulesQuery(String description) {
        return java.util.regex.Pattern.compile("(?iu)\\b(casa|vivienda|residencial|habitacional|unifamiliar|bifamiliar|edificabilidad|ocupaci[oó]n|construcci[oó]n|\\u00edndices?)\\b")
                .matcher(description).find();
    }

    private String buildRetrievalQuery(String description, boolean prioritizeBuildingRules) {
        if (!prioritizeBuildingRules) {
            return description;
        }
        return description + "\nNormativa de vivienda residencial, uso residencial del suelo, edificabilidad para predio individual, "
                + "índice de ocupación máximo, índice de construcción máximo, artículo 250 tipos de edificabilidad en suelo urbano, "
                + "tabla de edificabilidades 0 1 2 y 3, altura máxima y código morfológico de alturas, "
                + "tratamiento urbanístico y ficha normativa predial. Excluir límites que solo aplican a equipamientos.";
    }

    private void validateOfficialSource(String sourceUrl) {
        try {
            java.net.URI uri = java.net.URI.create(sourceUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("gov.co")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".gov.co"))) {
                throw new IllegalArgumentException("La fuente debe ser una URL HTTPS de un dominio oficial .gov.co.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("La fuente debe ser una URL HTTPS de un dominio oficial .gov.co.", exception);
        }
    }

    private ComplianceChecklistResponse pendingResponse(ProjectAnalysisResponse analysis, List<CitedRegulationResponse> citations) {
        return new ComplianceChecklistResponse("", "Por verificar", citations, OffsetDateTime.now(), ChecklistGenerationMode.PENDING, analysis);
    }

    private record QueryCacheKey(ComplianceQueryRequest request, long knowledgeVersion) {
    }
}
