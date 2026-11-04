package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.DocumentIngestionRequest;
import com.normbuild.regulation.dto.DocumentIngestionResponse;
import com.normbuild.regulation.dto.PdfDocumentIngestionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RegulatoryPdfIngestionService {

    private static final int INGESTION_BATCH_SIZE = 2;

    private final DocumentParsingService documentParsingService;
    private final RegulatoryTextChunker textChunker;
    private final RegulatoryRagService ragService;

    public RegulatoryPdfIngestionService(
            DocumentParsingService documentParsingService,
            RegulatoryTextChunker textChunker,
            RegulatoryRagService ragService) {
        this.documentParsingService = documentParsingService;
        this.textChunker = textChunker;
        this.ragService = ragService;
    }

    public CompletableFuture<PdfDocumentIngestionResponse> ingest(
            MultipartFile file,
            String title,
            String jurisdiction,
            String regulationCode,
            String sourceUrl) {
        UUID sourceDocumentId = UUID.randomUUID();
        return documentParsingService.extractPdfTextAsync(readBytes(file))
                .thenCompose(text -> indexChunks(text, title, jurisdiction, regulationCode, sourceUrl)
                        .thenApply(count -> new PdfDocumentIngestionResponse(sourceDocumentId, count, "INDEXED")));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 40L * 1024 * 1024) {
            throw new IllegalArgumentException("Adjunta un PDF de hasta 40 MB.");
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el archivo recibido.", exception);
        }
    }

    private CompletableFuture<Integer> indexChunks(
            String text,
            String title,
            String jurisdiction,
            String regulationCode,
            String sourceUrl) {
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("El documento no contiene texto para indexar.");
        }
        CompletableFuture<Integer> indexed = CompletableFuture.completedFuture(0);
        for (int offset = 0; offset < chunks.size(); offset += INGESTION_BATCH_SIZE) {
            int start = offset;
            int end = Math.min(start + INGESTION_BATCH_SIZE, chunks.size());
            indexed = indexed.thenCompose(count -> {
                List<CompletableFuture<DocumentIngestionResponse>> batch = new ArrayList<>();
                for (int chunkIndex = start; chunkIndex < end; chunkIndex++) {
                    String articleReference = "Sección " + (chunkIndex + 1) + " de " + chunks.size();
                    batch.add(ragService.ingestDocument(new DocumentIngestionRequest(
                            title, jurisdiction, regulationCode, articleReference, sourceUrl, chunks.get(chunkIndex))));
                }
                return CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new))
                        .thenApply(ignored -> count + (int) batch.stream()
                                .filter(future -> future.join().status().equals("INDEXED")).count());
            });
        }
        return indexed;
    }
}
