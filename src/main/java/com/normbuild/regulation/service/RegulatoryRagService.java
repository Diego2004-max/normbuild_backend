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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
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
    private final RegulatoryWebResearchService webResearchService;
    private final SimpleRegulatoryGuidanceService simpleGuidanceService;
    private final AtomicLong knowledgeVersion = new AtomicLong();
    @Value("${NORMBUILD_EMBEDDINGS_ENABLED:true}")
    private boolean embeddingsEnabled = true;
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
            RegulatoryAnswerSanitizer answerSanitizer,
            RegulatoryWebResearchService webResearchService,
            SimpleRegulatoryGuidanceService simpleGuidanceService
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
        this.webResearchService = webResearchService;
        this.simpleGuidanceService = simpleGuidanceService;
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
        var simpleGuidance = simpleGuidanceService.answer(request, analysis);
        if (simpleGuidance.isPresent()) {
            responseCache.put(cacheKey, simpleGuidance.get());
            return CompletableFuture.completedFuture(simpleGuidance.get());
        }
        onProgress.accept(pendingResponse(analysis, List.of()));
        boolean prioritizeBuildingRules = isBuildingRulesQuery(request.projectDescription());
        boolean prioritizeLicensing = isLicensingQuery(request.projectDescription());
        String retrievalQuery = buildRetrievalQuery(request.projectDescription(), prioritizeBuildingRules, prioritizeLicensing);
        int contextLimit = prioritizeBuildingRules && prioritizeLicensing ? Math.min(4, properties.maxContextResults())
                : prioritizeLicensing ? Math.min(3, properties.maxContextResults())
                : prioritizeBuildingRules ? Math.min(2, properties.maxContextResults()) : properties.maxContextResults();
        CompletableFuture<List<Double>> embeddingFuture = embeddingsEnabled
                ? CompletableFuture.supplyAsync(() -> embeddingClient.createEmbedding(retrievalQuery), ragTaskExecutor)
                : CompletableFuture.failedFuture(new AiProviderException("Embeddings disabled for this deployment."));
        CompletableFuture<List<RegulatoryDocumentProjection>> contextFuture = embeddingFuture.handleAsync((embedding, failure) -> {
            String jurisdiction = JurisdictionNormalizer.canonicalize(request.jurisdiction());
            if (failure != null) {
                LOGGER.warn("Embedding provider unavailable; using indexed official sources for {}", jurisdiction);
                return findIndexedFallback(jurisdiction, contextLimit, prioritizeBuildingRules, prioritizeLicensing);
            }
            String vectorLiteral = embeddingFormatter.toVectorLiteral(embedding);
            List<RegulatoryDocumentProjection> semanticContext = documentRepository.findNearestRegulations(
                    JurisdictionNormalizer.canonicalize(request.jurisdiction()),
                    vectorLiteral,
                    properties.similarityThreshold(),
                    prioritizeBuildingRules,
                    isSpecializedHousingQuery(request.projectDescription()),
                    prioritizeLicensing,
                    contextLimit
            );
            if (!semanticContext.isEmpty()) {
                return semanticContext;
            }
            List<RegulatoryDocumentProjection> officialFallback = findIndexedFallback(
                    jurisdiction, contextLimit, prioritizeBuildingRules, prioritizeLicensing);
            if (!officialFallback.isEmpty()) {
                LOGGER.info("Semantic retrieval returned no rows; using indexed official fallback for {}", jurisdiction);
            }
            return officialFallback;
        }, ragTaskExecutor);
        return contextFuture.thenApply(context -> filterProjectContext(context, request.projectDescription(), contextLimit))
                .thenCompose(context -> {
            List<CitedRegulationResponse> indexedCitations = mapCitations(context);
            String requestedJurisdiction = JurisdictionNormalizer.canonicalize(request.jurisdiction());
            boolean hasMunicipalContext = context.stream().anyMatch(document ->
                    requestedJurisdiction.equals(JurisdictionNormalizer.canonicalize(document.getJurisdiction())));
            if (!hasMunicipalContext && !requestedJurisdiction.equals("Colombia")) {
                try {
                    return webResearchService.research(request, onProgress)
                            .exceptionally(failure -> unavailableResearch(analysis));
                } catch (ResponseStatusException exception) {
                    return CompletableFuture.completedFuture(unavailableResearch(analysis));
                }
            }
            LOGGER.info("Using indexed official regulations for {} without external-search latency",
                    requestedJurisdiction);
            return CompletableFuture.completedFuture(
                    generateFromIndexedContext(request, analysis, context, cacheKey, onProgress));
        });
    }

    private ComplianceChecklistResponse unavailableResearch(ProjectAnalysisResponse analysis) {
        return new ComplianceChecklistResponse(
                "La búsqueda de fuentes oficiales no está disponible en este momento. No se generó un checklist ni se evaluó el cumplimiento.",
                "Por verificar", List.of(), OffsetDateTime.now(), ChecklistGenerationMode.NO_CONTEXT, analysis);
    }

    private List<RegulatoryDocumentProjection> findIndexedFallback(
            String jurisdiction, int limit, boolean prioritizeBuildingRules, boolean prioritizeLicensing) {
        String query = prioritizeBuildingRules && prioritizeLicensing
                ? "vivienda OR edificabilidad OR ocupación OR altura OR aislamientos OR licencia"
                : prioritizeLicensing
                ? "licencia OR documentos OR radicación"
                : "vivienda OR edificabilidad OR ocupación OR altura OR aislamientos";
        List<RegulatoryDocumentProjection> relevant = documentRepository.findRelevantOfficialRegulations(jurisdiction, query, limit);
        if (!relevant.isEmpty() || !prioritizeLicensing) {
            return relevant;
        }
        return documentRepository.findRelevantOfficialRegulations(jurisdiction,
                "vivienda OR edificabilidad OR ocupación OR altura OR aislamientos", limit);
    }

    private ComplianceChecklistResponse generateFromIndexedContext(
            ComplianceQueryRequest request, ProjectAnalysisResponse analysis,
            List<RegulatoryDocumentProjection> context, QueryCacheKey cacheKey,
            Consumer<ComplianceChecklistResponse> onProgress) {
            String requestedJurisdiction = JurisdictionNormalizer.canonicalize(request.jurisdiction());
            boolean hasLocalMunicipalContext = context.stream().anyMatch(document -> document.getJurisdiction() != null
                    && requestedJurisdiction.equals(JurisdictionNormalizer.canonicalize(document.getJurisdiction())));
            if (!hasLocalMunicipalContext && !requestedJurisdiction.equals("Colombia")) {
                return new ComplianceChecklistResponse("", "Por verificar", List.of(), OffsetDateTime.now(),
                        ChecklistGenerationMode.NEEDS_WEB_RESEARCH, analysis);
            }
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
                String answer = answerSanitizer.sanitize(llmClient.generateChecklist(prompt), analysis, mapCitations(context), request.projectDescription());
                LOGGER.info("Checklist completed: mode=GENERATIVE, sources={}", context.size());
                ComplianceChecklistResponse response = new ComplianceChecklistResponse(answer, "Por verificar",
                        mapCitations(context), OffsetDateTime.now(), ChecklistGenerationMode.GENERATIVE, analysis);
                responseCache.put(cacheKey, response);
                return response;
            } catch (AiProviderException exception) {
                LOGGER.warn("LLM generation unavailable; returning source references without a generated checklist", exception);
                return new ComplianceChecklistResponse(
                        AiProviderFailureMessages.forCitizen(exception)
                                + " No se generó un checklist ni se evaluó el cumplimiento. Las fuentes oficiales recuperadas se conservan como referencia.",
                        "Por verificar",
                        mapCitations(context),
                        OffsetDateTime.now(),
                        ChecklistGenerationMode.UNAVAILABLE,
                        analysis
                );
            }
    }

    @Transactional
    public CompletableFuture<DocumentIngestionResponse> ingestDocument(DocumentIngestionRequest request) {
        validateOfficialSource(request.sourceUrl());
        String jurisdiction = JurisdictionNormalizer.canonicalize(request.jurisdiction());
        CompletableFuture<String> normalizedFuture = parsingService.normalizeContentAsync(request.content());
        return normalizedFuture.thenApplyAsync(normalized -> {
            List<Double> embedding = embeddingsEnabled
                    ? embeddingClient.createEmbedding(normalized)
                    : java.util.Collections.nCopies(properties.embeddingDimensions(), 0.0d);
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

    private boolean isLicensingQuery(String description) {
        return java.util.regex.Pattern.compile("(?iu)\\b(licencias?|licenciamiento|radicar|tramitar)\\b")
                .matcher(description).find();
    }

    private boolean isSpecializedHousingQuery(String description) {
        return java.util.regex.Pattern.compile("(?iu)\\bvip\\b")
                .matcher(description).find();
    }

    static List<RegulatoryDocumentProjection> filterTerritorialContext(
            List<RegulatoryDocumentProjection> context, String projectDescription) {
        boolean urbanProject = java.util.regex.Pattern.compile("(?iu)\\b(?:urbano|urbana|zona urbana|suelo urbano)\\b")
                .matcher(projectDescription).find();
        boolean ruralProject = java.util.regex.Pattern.compile("(?iu)\\b(?:rural|campestre|centro poblado)\\b")
                .matcher(projectDescription).find();
        if (!urbanProject || ruralProject) return context;
        return context.stream().filter(document -> {
            String searchable = ((document.getArticleReference() == null ? "" : document.getArticleReference()) + " "
                    + (document.getContent() == null ? "" : document.getContent()));
            return !java.util.regex.Pattern.compile(
                    "(?iu)\\b(?:centros? poblados? rurales?|suelo rural|edificabilidad rural|parcelaci[oó]n rural)\\b")
                    .matcher(searchable).find();
        }).toList();
    }

    static List<RegulatoryDocumentProjection> filterProjectContext(
            List<RegulatoryDocumentProjection> context, String projectDescription, int limit) {
        List<RegulatoryDocumentProjection> territorial = filterTerritorialContext(context, projectDescription);
        boolean specializedProject = java.util.regex.Pattern.compile(
                "(?iu)\\b(?:patrimonio|inter[eé]s cultural|bic|equipamiento|deportivo|recreativo|plan de implantaci[oó]n)\\b")
                .matcher(projectDescription).find();
        boolean concreteProductionProject = java.util.regex.Pattern.compile(
                "(?iu)\\b(?:planta|producci[oó]n)\\s+(?:m[oó]vil\\s+)?de\\s+concreto\\b")
                .matcher(projectDescription).find();
        return territorial.stream().filter(document -> {
            if (specializedProject) return true;
            String searchable = ((document.getTitle() == null ? "" : document.getTitle()) + " "
                    + (document.getArticleReference() == null ? "" : document.getArticleReference()) + " "
                    + (document.getContent() == null ? "" : document.getContent()));
            boolean unrelatedSpecializedRule = java.util.regex.Pattern.compile(
                    "(?iu)\\b(?:bienes? de inter[eé]s cultural|tratamiento de conservaci[oó]n|niveles? de conservaci[oó]n|equipamientos? deportivos?|equipamientos? recreativos?|plan(?:es)? de implantaci[oó]n|regularizaci[oó]n y manejo)\\b")
                    .matcher(searchable).find();
            boolean concreteProductionRule = java.util.regex.Pattern.compile(
                    "(?iu)\\b(?:plantas? de producci[oó]n de concreto|planta m[oó]vil de concreto|concreto en obra)\\b")
                    .matcher(searchable).find();
            return !unrelatedSpecializedRule && (concreteProductionProject || !concreteProductionRule);
        }).limit(limit).toList();
    }

    private String buildRetrievalQuery(String description, boolean prioritizeBuildingRules, boolean prioritizeLicensing) {
        StringBuilder query = new StringBuilder(description);
        if (prioritizeBuildingRules) {
            query.append("\nResidential urban land use, individual parcel building potential, maximum lot occupancy and construction indexes, "
                    + "urban height and morphology rules, planning treatment and official parcel zoning records. Exclude equipment-only rules.");
        }
        if (prioritizeLicensing) {
            query.append("\nResidential building permit and urban planning license. Decree 1077 of 2015, construction license application, "
                    + "required documents and filing process with the urban curatorship or municipal authority.");
        }
        return query.toString();
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
