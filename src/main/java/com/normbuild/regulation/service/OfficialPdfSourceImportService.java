package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.OfficialSourceImportRequest;
import com.normbuild.regulation.dto.PdfDocumentIngestionResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfficialPdfSourceImportService {

    private static final int MAX_SOURCE_SIZE_BYTES = 40 * 1024 * 1024;
    private final WebClient webClient;
    private final RegulatoryPdfIngestionService pdfIngestionService;

    public OfficialPdfSourceImportService(
            WebClient.Builder webClientBuilder,
            RegulatoryPdfIngestionService pdfIngestionService) {
        this.webClient = webClientBuilder.clone()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_SOURCE_SIZE_BYTES))
                .build();
        this.pdfIngestionService = pdfIngestionService;
    }

    public CompletableFuture<PdfDocumentIngestionResponse> importReviewedSource(OfficialSourceImportRequest request) {
        if (!request.legalReviewConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirma que revisaste la vigencia, modificaciones y alcance del documento antes de indexarlo.");
        }
        URI source = validateOfficialSourceUrl(request.sourceUrl());
        return webClient.get()
                .uri(source)
                .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_HTML,
                        MediaType.parseMediaType("application/xhtml+xml"))
                .retrieve()
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(45))
                .flatMap(entity -> {
                    byte[] bytes = entity.getBody();
                    if (bytes == null || bytes.length == 0 || bytes.length > MAX_SOURCE_SIZE_BYTES) {
                        return reactor.core.publisher.Mono.error(new ResponseStatusException(
                                HttpStatus.PAYLOAD_TOO_LARGE, "La fuente oficial está vacía o supera el límite de 40 MB."));
                    }
                    MediaType contentType = entity.getHeaders().getContentType();
                    if (isPdf(bytes, contentType)) {
                        return reactor.core.publisher.Mono.fromFuture(pdfIngestionService.ingest(
                                bytes, request.title().strip(), request.jurisdiction().strip(),
                                request.regulationCode().strip(), source.toString()));
                    }
                    if (isHtml(contentType) && bytes.length <= 10 * 1024 * 1024) {
                        Charset charset = contentType.getCharset() == null ? StandardCharsets.UTF_8 : contentType.getCharset();
                        return reactor.core.publisher.Mono.fromFuture(pdfIngestionService.ingestHtml(
                                bytes, charset, request.title().strip(), request.jurisdiction().strip(),
                                request.regulationCode().strip(), source.toString()));
                    }
                    return reactor.core.publisher.Mono.error(new ResponseStatusException(
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "La fuente debe ser un PDF con texto o una página HTML oficial de hasta 10 MB."));
                })
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "No se pudo descargar o procesar la fuente oficial. Verifica su disponibilidad y contenido.", error))
                .toFuture();
    }

    private static boolean isPdf(byte[] bytes, MediaType contentType) {
        boolean pdfSignature = bytes.length >= 5
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
        return pdfSignature || contentType != null && MediaType.APPLICATION_PDF.isCompatibleWith(contentType);
    }

    private static boolean isHtml(MediaType contentType) {
        return contentType != null && (MediaType.TEXT_HTML.isCompatibleWith(contentType)
                || MediaType.parseMediaType("application/xhtml+xml").isCompatibleWith(contentType));
    }

    static URI validateOfficialSourceUrl(String sourceUrl) {
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
