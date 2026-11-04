package com.normbuild.regulation.service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentParsingService {

    private static final int MAX_PDF_PAGES = 2000;

    @Async("ragTaskExecutor")
    public CompletableFuture<String> normalizeContentAsync(String content) {
        String normalized = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("\\s+", " "))
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        return CompletableFuture.completedFuture(normalized);
    }

    @Async("ragTaskExecutor")
    public CompletableFuture<String> buildSearchSummaryAsync(String content) {
        String summary = Arrays.stream(content.split("\\."))
                .map(String::trim)
                .filter(sentence -> sentence.length() > 24)
                .limit(6)
                .reduce("", (left, right) -> left.isBlank() ? right : left + ". " + right);
        return CompletableFuture.completedFuture(summary.isBlank() ? content : summary);
    }

    @Async("ragTaskExecutor")
    public CompletableFuture<String> extractPdfTextAsync(byte[] pdfBytes) {
        if (pdfBytes.length < 5 || !new String(pdfBytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IllegalArgumentException("El archivo no tiene una estructura PDF válida.");
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException("El documento supera el máximo de 2.000 páginas.");
            }
            String text = new PDFTextStripper().getText(document).strip();
            if (text.length() < 80) {
                throw new IllegalArgumentException("No se pudo extraer texto. Verifica que el PDF no sea un escaneo sin texto seleccionable.");
            }
            return CompletableFuture.completedFuture(text);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el documento PDF.", exception);
        }
    }
}
