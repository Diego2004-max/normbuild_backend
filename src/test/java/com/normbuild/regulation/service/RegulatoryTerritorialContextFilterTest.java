package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.normbuild.regulation.repository.RegulatoryDocumentProjection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulatoryTerritorialContextFilterTest {

    @Test
    void excludesRuralArticlesFromAnExplicitlyUrbanProject() {
        RegulatoryDocumentProjection urban = document("Artículo 310", "Normas de edificabilidad urbana.");
        RegulatoryDocumentProjection rural = document("Artículo 461", "Edificabilidad de centros poblados rurales.");

        List<RegulatoryDocumentProjection> result = RegulatoryRagService.filterTerritorialContext(
                List.of(rural, urban), "Vivienda en un lote ubicado en zona urbana.");

        assertThat(result).containsExactly(urban);
    }

    @Test
    void keepsRuralArticlesWhenTheProjectRequestsRuralRules() {
        RegulatoryDocumentProjection rural = document("Artículo 461", "Edificabilidad de centros poblados rurales.");

        List<RegulatoryDocumentProjection> result = RegulatoryRagService.filterTerritorialContext(
                List.of(rural), "Vivienda campestre en suelo rural.");

        assertThat(result).containsExactly(rural);
    }

    @Test
    void excludesHeritageAndEquipmentRulesFromAnOrdinaryHomeProject() {
        RegulatoryDocumentProjection home = document("Artículo 310", "Edificabilidad para vivienda urbana.");
        RegulatoryDocumentProjection heritage = document("Artículo 595", "Actuaciones en bienes de interés cultural.");
        RegulatoryDocumentProjection equipment = document("Artículo 600", "Planes para equipamientos deportivos y recreativos.");

        List<RegulatoryDocumentProjection> result = RegulatoryRagService.filterProjectContext(
                List.of(heritage, equipment, home), "Vivienda bifamiliar urbana y licencia de construcción.", 4);

        assertThat(result).containsExactly(home);
    }

    private RegulatoryDocumentProjection document(String reference, String content) {
        RegulatoryDocumentProjection projection = mock(RegulatoryDocumentProjection.class);
        when(projection.getArticleReference()).thenReturn(reference);
        when(projection.getContent()).thenReturn(content);
        return projection;
    }
}
