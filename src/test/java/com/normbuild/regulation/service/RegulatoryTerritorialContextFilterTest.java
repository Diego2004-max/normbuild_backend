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

    private RegulatoryDocumentProjection document(String reference, String content) {
        RegulatoryDocumentProjection projection = mock(RegulatoryDocumentProjection.class);
        when(projection.getArticleReference()).thenReturn(reference);
        when(projection.getContent()).thenReturn(content);
        return projection;
    }
}
