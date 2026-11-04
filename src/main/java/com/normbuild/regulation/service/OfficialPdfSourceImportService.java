package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.OfficialSourceImportRequest;
import com.normbuild.regulation.dto.PdfDocumentIngestionResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfficialPdfSourceImportService {

    private static final int MAX_PDF_SIZE_BYTES = 40 * 1024 * 1024;
    private final WebClient webClient;
    private final RegulatoryPdfIngestionService pdfIngestionService;

    public OfficialPdfSourceImportService(
            WebClient.Builder webClientBuilder,
            RegulatoryPdfIngestionService pdfIngestionService) {
        this.webClient = webClientBuilder.clone()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_PDF_SIZE_BYTES))
                .build();
        this.pdfIngestionService = pdfIngestionService;
    }

    public CompletableFuture<PdfDocumentIngestionResponse> importReviewedSource(OfficialSourceImportRequest request) {
        if (!request.legalReviewConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirma que revisaste la vigencia, modificaciones y alcance del documento antes de indexarlo.");
        }
        URI source = validateOfficialPdfUrl(request.sourceUrl());
        return webClient.get()
                .uri(source)
                .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(45))
                .flatMap(bytes -> {
                    if (bytes.length > MAX_PDF_SIZE_BYTES) {
                        return reactor.core.publisher.Mono.error(new ResponseStatusException(
                                HttpStatus.PAYLOAD_TOO_LARGE, "El PDF oficial supera el límite de 40 MB."));
                    }
                    return reactor.core.publisher.Mono.fromFuture(pdfIngestionService.ingest(
                            bytes, request.title().strip(), request.jurisdiction().strip(),
                            request.regulationCode().strip(), source.toString()));
                })
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "No se pudo descargar o procesar el PDF oficial. Verifica que la fuente sea un PDF con texto seleccionable.", error))
                .toFuture();
    }

    static URI validateOfficialPdfUrl(String sourceUrl) {
        try {
            URI source = URI.create(sourceUrl.strip());
            String host = source.getHost();
            if (!"https".equalsIgnoreCase(source.getScheme()) || host == null
                    || source.getUserInfo() != null || source.getPort() != -1 && source.getPort() != 443
                    || !host.toLowerCase(Locale.ROOT).matches("(?:[a-z0-9-]+\\.)+gov\\.co")) {
                throw new IllegalArgumentException();
            }
            return source;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La URL debe ser HTTPS y pertenecer a un dominio oficial colombiano .gov.co.");
        }
    }
}
