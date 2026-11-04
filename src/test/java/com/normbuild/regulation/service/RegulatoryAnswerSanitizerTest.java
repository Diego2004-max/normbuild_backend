package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import com.normbuild.regulation.dto.CitedRegulationResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegulatoryAnswerSanitizerTest {

    private final RegulatoryAnswerSanitizer sanitizer = new RegulatoryAnswerSanitizer();

    @Test
    void keepsCalculatedProposalRatiosAndRemovesUnverifiedLegalLimits() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(
                200.0, 100.0, 200.0, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.valueOf(100), List.of());
        List<CitedRegulationResponse> citations = List.of(
                new CitedRegulationResponse(UUID.randomUUID(), "POT Pasto", "Acuerdo 004 de 2015",
                        "Artículo 250 (parte 1 de 4)", "https://pasto.gov.co/norma", 0.9, "Edificabilidades y índices."),
                new CitedRegulationResponse(UUID.randomUUID(), "POT Pasto", "Acuerdo 004 de 2015",
                        "Artículo 267", "https://pasto.gov.co/norma", 0.8, "Aislamientos para edificación aislada."));

        String answer = sanitizer.sanitize(
                "**La casa requiere revisión.**\n1. El índice máximo permitido es 0,50 para edificaciones aisladas [1].\nRevisa la ficha normativa.",
                analysis, citations, "Casa unifamiliar en un lote urbano.");

        assertThat(answer).contains("100 / 200 m² = 0,50 (50%)", "200 / 200 m² = 1,00 (100%)");
        assertThat(answer).contains("La casa requiere revisión", "Revisa la ficha normativa");
        assertThat(answer).doesNotContain("máximo permitido es 0,50");
        assertThat(answer).doesNotContain("*");
        assertThat(answer).contains("tratamiento urbanístico, uso permitido y tipología");
        assertThat(answer).contains("tipo de edificabilidad y el código morfológico de altura", "[1]");
        assertThat(answer).contains("Si la edificación es aislada", "[2]");
    }

    @Test
    void removesParcelClassificationsNotPresentInTheCitizenDescription() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(null, null, null, null, null, null, List.of());

        String answer = sanitizer.sanitize(
                "Revisa la zona 5 y el poligono Z5_CN3_32 antes de disenar.", analysis, List.of(),
                "Casa en zona 3 urbana.");

        assertThat(answer).doesNotContain("zona 5", "poligono Z5_CN3_32");
        assertThat(answer).contains("edificabilidad y altura aplicables");
    }

    @Test
    void explainsMedellinParcelSpecificOccupancyRulesWithArticleCitations() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(
                180.0, 90.0, 180.0, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.valueOf(90), List.of());
        List<CitedRegulationResponse> citations = List.of(
                new CitedRegulationResponse(UUID.randomUUID(), "POT Medellin", "Acuerdo Municipal 048 de 2014",
                        "Articulo 280 (parte 8 de 12)", "https://www.medellin.gov.co/pot.pdf", 0.8, "Tabla por codigo de poligono."),
                new CitedRegulationResponse(UUID.randomUUID(), "POT Medellin", "Acuerdo Municipal 048 de 2014",
                        "Articulo 282 (parte 1 de 2)", "https://www.medellin.gov.co/pot.pdf", 0.8, "Indice de ocupacion, tratamiento y patios y vacios."));

        String answer = sanitizer.sanitize("La norma depende de la ficha del predio.\nPara verificar las reglas, sigue estos pasos:\n\nVerificacion de indices\n\n", analysis, citations,
                "Vivienda unifamiliar en Medellin.");

        assertThat(answer).contains("280", "pol", "[1]");
        assertThat(answer).contains("282", "patios y", "[2]");
        assertThat(answer).contains("50% propuesto");
        assertThat(answer).doesNotContain("no estan definidos");
        assertThat(answer).doesNotContain("sigue estos pasos", "Verificacion de indices");
    }

    @Test
    void doesNotApplyPastoArticleRulesToOtherMunicipalities() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(null, null, null, null, null, null, List.of());
        List<CitedRegulationResponse> citations = List.of(
                new CitedRegulationResponse(UUID.randomUUID(), "POT Medellin", "Acuerdo Municipal 048 de 2014",
                        "Articulo 250", "https://www.medellin.gov.co/pot.pdf", 0.8, "Contenido del articulo."),
                new CitedRegulationResponse(UUID.randomUUID(), "POT Medellin", "Acuerdo Municipal 048 de 2014",
                        "Articulo 267", "https://www.medellin.gov.co/pot.pdf", 0.8, "Contenido del articulo."));

        String answer = sanitizer.sanitize("Revisa la normativa.", analysis, citations, "Casa unifamiliar.");

        assertThat(answer).doesNotContain("c?digo morfol?gico", "edificaci?n es aislada");
    }

    @Test
    void explainsBucaramangaNetAreaAndMaximumIndexRulesWithCitations() {
        ProjectAnalysisResponse analysis = new ProjectAnalysisResponse(
                180.0, 90.0, 180.0, BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.valueOf(90), List.of());
        List<CitedRegulationResponse> citations = List.of(
                new CitedRegulationResponse(UUID.randomUUID(), "POT Bucaramanga", "Acuerdo Municipal 011 de 2014",
                        "Articulo 310", "https://www.bucaramanga.gov.co/pot.pdf", 0.8, "Los indices aplican sobre area neta."),
                new CitedRegulationResponse(UUID.randomUUID(), "POT Bucaramanga", "Acuerdo Municipal 011 de 2014",
                        "Articulo 313", "https://www.bucaramanga.gov.co/pot.pdf", 0.8, "Los proyectos no pueden superar indices."));

        String answer = sanitizer.sanitize("Los indices de ocupacion y construccion no se pueden calcular con la informacion proporcionada. Para determinar si se aplica el articulo 310 o 313, revisa la ficha.", analysis, citations,
                "Vivienda unifamiliar en lote urbano.");

        assertThat(answer).contains("neta del predio", "180 m", "[1]");
        assertThat(answer).contains("no debe superar", "excepciones", "[2]");
        assertThat(answer).doesNotContain("no se pueden calcular", "determinar si se aplica el articulo 310 o 313");
    }
}
