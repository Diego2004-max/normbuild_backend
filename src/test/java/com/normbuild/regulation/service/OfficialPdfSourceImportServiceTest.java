package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.normbuild.regulation.dto.OfficialSourceImportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

class OfficialPdfSourceImportServiceTest {

    @Test
    void acceptsOnlySecureColombianGovernmentUrls() {
        assertThat(OfficialPdfSourceImportService.validateOfficialPdfUrl(
                "https://planeacion.municipio.gov.co/pot.pdf").getHost())
                .isEqualTo("planeacion.municipio.gov.co");
        assertThatThrownBy(() -> OfficialPdfSourceImportService.validateOfficialPdfUrl(
                "http://municipio.gov.co/pot.pdf")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OfficialPdfSourceImportService.validateOfficialPdfUrl(
                "https://example.com/pot.pdf")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> OfficialPdfSourceImportService.validateOfficialPdfUrl(
                "https://user@municipio.gov.co/pot.pdf")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requiresAnExplicitLegalReviewBeforeImporting() {
        var service = new OfficialPdfSourceImportService(WebClient.builder(), null);
        var request = new OfficialSourceImportRequest("Plan de Ordenamiento", "Municipio",
                "Acuerdo 001 de 2020", "https://municipio.gov.co/pot.pdf", false);

        assertThatThrownBy(() -> service.importReviewedSource(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vigencia");
    }
}
