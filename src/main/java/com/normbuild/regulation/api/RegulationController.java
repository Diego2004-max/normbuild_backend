package com.normbuild.regulation.api;

import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.DocumentIngestionRequest;
import com.normbuild.regulation.dto.DocumentIngestionResponse;
import com.normbuild.regulation.dto.PdfDocumentIngestionRequest;
import com.normbuild.regulation.dto.PdfDocumentIngestionResponse;
import com.normbuild.regulation.dto.JurisdictionCoverageResponse;
import com.normbuild.regulation.repository.RegulatoryDocumentJdbcRepository;
import com.normbuild.regulation.service.RegulatoryRagService;
import com.normbuild.regulation.service.RegulatoryPdfIngestionService;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/regulations")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class RegulationController {

    private final RegulatoryRagService ragService;
    private final RegulatoryPdfIngestionService pdfIngestionService;
    private final String ingestionApiKey;
    private final RegulatoryDocumentJdbcRepository documentRepository;

    public RegulationController(
            RegulatoryRagService ragService,
            RegulatoryPdfIngestionService pdfIngestionService,
            RegulatoryDocumentJdbcRepository documentRepository,
            @Value("${normbuild.security.ingestion-api-key:}") String ingestionApiKey) {
        this.ragService = ragService;
        this.pdfIngestionService = pdfIngestionService;
        this.documentRepository = documentRepository;
        this.ingestionApiKey = ingestionApiKey;
    }

    @GetMapping("/coverage")
    public List<JurisdictionCoverageResponse> getCoverage() {
        return documentRepository.findJurisdictionCoverage();
    }

    @PostMapping("/compliance-checklist")
    public CompletableFuture<ComplianceChecklistResponse> createComplianceChecklist(@Valid @RequestBody ComplianceQueryRequest request) {
        return ragService.answerComplianceQuestion(request);
    }

    @PostMapping(value = "/compliance-checklist/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamComplianceChecklist(@Valid @RequestBody ComplianceQueryRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        emitter.onTimeout(() -> {
            sendEvent(emitter, closed, "error", new ApiErrorResponse(
                    "El análisis agotó el tiempo disponible. Intenta nuevamente.", List.of(), OffsetDateTime.now()));
            closed.set(true);
            emitter.complete();
        });
        ragService.answerComplianceQuestion(request, progress -> sendEvent(emitter, closed, "progress", progress))
                .whenComplete((response, error) -> {
                    if (closed.get()) {
                        return;
                    }
                    if (error == null) {
                        sendEvent(emitter, closed, "complete", response);
                    } else {
                        sendEvent(emitter, closed, "error", new ApiErrorResponse(
                                "No fue posible completar la revisión normativa. Comprueba que Ollama esté disponible.",
                                List.of(), OffsetDateTime.now()));
                    }
                    closed.set(true);
                    emitter.complete();
                });
        return ResponseEntity.ok().header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no").body(emitter);
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String event, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            closed.set(true);
        }
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<DocumentIngestionResponse> ingestDocument(
            @Valid @RequestBody DocumentIngestionRequest request,
            @RequestHeader(value = "X-NormBuild-Ingestion-Key", required = false) String providedKey) {
        authorizeIngestion(providedKey);
        return ragService.ingestDocument(request);
    }

    @PostMapping(value = "/documents/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<PdfDocumentIngestionResponse> ingestPdfDocument(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") PdfDocumentIngestionRequest metadata,
            @RequestHeader(value = "X-NormBuild-Ingestion-Key", required = false) String providedKey) {
        authorizeIngestion(providedKey);
        return pdfIngestionService.ingest(file, metadata.title(), metadata.jurisdiction(),
                metadata.regulationCode(), metadata.sourceUrl());
    }

    private void authorizeIngestion(String providedKey) {
        if (ingestionApiKey.isBlank() || providedKey == null
                || !MessageDigest.isEqual(ingestionApiKey.getBytes(StandardCharsets.UTF_8), providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una clave de administración válida.");
        }
    }
}
