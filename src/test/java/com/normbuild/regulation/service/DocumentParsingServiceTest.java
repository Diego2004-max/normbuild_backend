package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class DocumentParsingServiceTest {

    private final DocumentParsingService documentParsingService = new DocumentParsingService();

    @Test
    void rejectsFilesWithoutPdfSignature() {
        assertThatThrownBy(() -> documentParsingService.extractPdfTextAsync("not a PDF".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estructura PDF");
    }

    @Test
    void extractsSelectableTextFromOfficialPdfBytes() throws Exception {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(48, 720);
                content.showText("Official planning regulation with searchable text and complete provisions. Additional requirements are described in this official municipal chapter.");
                content.endText();
            }
            document.save(output);
            pdfBytes = output.toByteArray();
        }

        String text = documentParsingService.extractPdfTextAsync(pdfBytes).join();

        assertThat(text).contains("Official planning regulation", "complete provisions");
    }

    @Test
    void extractsVisibleHtmlTextAndExcludesNavigationAndScripts() {
        String html = "<html><head><title>Hidden title</title></head><body>"
                + "<nav>Navigation must not be indexed</nav><main><h1>Plan oficial de ordenamiento territorial</h1>"
                + "<p>Este contenido normativo oficial establece condiciones urbanísticas para los predios del municipio.</p>"
                + "<script>secretToken()</script></main></body></html>";

        String text = documentParsingService.extractHtmlTextAsync(
                html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).join();

        assertThat(text).contains("Plan oficial", "condiciones urbanísticas")
                .doesNotContain("Navigation must not be indexed", "secretToken", "Hidden title");
    }
}
