package com.normbuild.regulation.service;

import java.util.Arrays;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DocumentParsingService {

    private static final int MAX_PDF_PAGES = 2000;
    private static final int MAX_HTML_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> EXCLUDED_HTML_TAGS = Set.of("head", "script", "style", "nav", "header", "footer", "noscript");
    private static final Set<String> BLOCK_HTML_TAGS = Set.of(
            "article", "blockquote", "br", "dd", "div", "dl", "dt", "h1", "h2", "h3", "h4", "h5", "h6",
            "li", "main", "ol", "p", "section", "table", "td", "th", "tr", "ul");

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

    @Async("ragTaskExecutor")
    public CompletableFuture<String> extractHtmlTextAsync(byte[] htmlBytes, Charset charset) {
        if (htmlBytes == null || htmlBytes.length == 0 || htmlBytes.length > MAX_HTML_SIZE_BYTES) {
            throw new IllegalArgumentException("La página oficial debe tener entre 1 byte y 10 MB.");
        }
        Charset sourceCharset = charset == null ? StandardCharsets.UTF_8 : charset;
        String html = new String(htmlBytes, sourceCharset)
                .replaceAll("(?is)<(head|script|style|nav|header|footer|noscript)\\b[^>]*>.*?</\\1\\s*>", " ");
        VisibleTextParser callback = new VisibleTextParser();
        try {
            new ParserDelegator().parse(new StringReader(html), callback, true);
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo interpretar el texto de la página oficial.", exception);
        }
        String text = callback.normalizedText();
        if (text.length() < 80) {
            throw new IllegalArgumentException("La página oficial no contiene suficiente texto visible para indexar.");
        }
        return CompletableFuture.completedFuture(text);
    }

    private static final class VisibleTextParser extends HTMLEditorKit.ParserCallback {

        private final StringBuilder text = new StringBuilder();
        private int excludedDepth;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            String name = tag.toString().toLowerCase(java.util.Locale.ROOT);
            if (EXCLUDED_HTML_TAGS.contains(name)) {
                excludedDepth++;
            } else if (excludedDepth == 0 && BLOCK_HTML_TAGS.contains(name)) {
                text.append('\n');
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            String name = tag.toString().toLowerCase(java.util.Locale.ROOT);
            if (EXCLUDED_HTML_TAGS.contains(name) && excludedDepth > 0) {
                excludedDepth--;
            } else if (excludedDepth == 0 && BLOCK_HTML_TAGS.contains(name)) {
                text.append('\n');
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (excludedDepth == 0 && BLOCK_HTML_TAGS.contains(tag.toString().toLowerCase(java.util.Locale.ROOT))) {
                text.append('\n');
            }
        }

        @Override
        public void handleText(char[] content, int position) {
            if (excludedDepth == 0) {
                text.append(content).append(' ');
            }
        }

        private String normalizedText() {
            return Arrays.stream(text.toString().split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.replaceAll("\\s+", " "))
                    .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
        }
    }
}
