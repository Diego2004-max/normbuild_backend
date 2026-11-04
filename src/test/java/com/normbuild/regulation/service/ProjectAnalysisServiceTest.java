package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import org.junit.jupiter.api.Test;

class ProjectAnalysisServiceTest {

    private final ProjectAnalysisService service = new ProjectAnalysisService(new ProjectFactExtractor());

    @Test
    void computesProposedIndicesFromTheActualUserExample() {
        ProjectAnalysisResponse analysis = service.analyze("Tengo un lote de 200 m². Quiero ocupar 100 m² en el primer piso y construir 200 m² en total, distribuidos en dos pisos con altura total de 6 metros.");

        assertThat(analysis.lotAreaSquareMeters()).isEqualTo(200.0);
        assertThat(analysis.occupiedAreaSquareMeters()).isEqualTo(100.0);
        assertThat(analysis.totalBuiltAreaSquareMeters()).isEqualTo(200.0);
        assertThat(analysis.occupancyIndex()).isEqualByComparingTo("0.5");
        assertThat(analysis.constructionIndex()).isEqualByComparingTo("1");
        assertThat(analysis.unoccupiedAreaSquareMeters()).isEqualByComparingTo("100");
        assertThat(analysis.observations()).anyMatch(message -> message.contains("no los máximos permitidos"));
    }

    @Test
    void doesNotConfuseFirstFloorAreaWithTotalAreaOrInferItFromFloorCount() {
        ProjectAnalysisResponse analysis = service.analyze("Lote de 200 m2. Quiero ocupar 100 m2 en el primer piso y construir dos pisos.");

        assertThat(analysis.occupancyIndex()).isEqualByComparingTo("0.5");
        assertThat(analysis.totalBuiltAreaSquareMeters()).isNull();
        assertThat(analysis.constructionIndex()).isNull();
    }

    @Test
    void doesNotUseUnlabelledAreaAsLotArea() {
        ProjectAnalysisResponse analysis = service.analyze("Quiero construir 200 metros cuadrados en total en dos pisos.");

        assertThat(analysis.totalBuiltAreaSquareMeters()).isEqualTo(200.0);
        assertThat(analysis.lotAreaSquareMeters()).isNull();
        assertThat(analysis.constructionIndex()).isNull();
    }

    @Test
    void rejectsContradictoryLotAreas() {
        ProjectAnalysisResponse analysis = service.analyze("Lote de 200 m², predio de 300 m². Quiero ocupar 100 m² y construir 200 m² en total.");

        assertThat(analysis.lotAreaSquareMeters()).isNull();
        assertThat(analysis.occupancyIndex()).isNull();
        assertThat(analysis.constructionIndex()).isNull();
    }

    @Test
    void rejectsNonPositiveLotArea() {
        ProjectAnalysisResponse analysis = service.analyze("Lote de 0 m². Quiero ocupar 100 m² y construir 200 m² en total.");

        assertThat(analysis.occupancyIndex()).isNull();
        assertThat(analysis.constructionIndex()).isNull();
        assertThat(analysis.unoccupiedAreaSquareMeters()).isNull();
    }

    @Test
    void detectsImpossibleAreaCombination() {
        ProjectAnalysisResponse analysis = service.analyze("Lote de 200 m². Quiero ocupar 250 m² y construir 100 m² en total.");

        assertThat(analysis.unoccupiedAreaSquareMeters()).isNull();
        assertThat(analysis.observations()).anyMatch(message -> message.contains("supera el área del lote"));
        assertThat(analysis.observations()).anyMatch(message -> message.contains("menor que el primer piso"));
    }

    @Test
    void handlesDecimalCommasAndExplicitBuiltAreaLabel() {
        ProjectAnalysisResponse analysis = service.analyze("Un lote de 200,5 m² con ocupación de 100,25 m² y área construida total de 401 m².");

        assertThat(analysis.occupancyIndex()).isEqualByComparingTo("0.5");
        assertThat(analysis.constructionIndex()).isEqualByComparingTo("2");
    }

    @Test
    void doesNotInterpretAmbiguousThousandsAsSmallDecimalAreas() {
        ProjectAnalysisResponse analysis = service.analyze("Lote de 1.200 m² con ocupación de 100 m² y área construida total de 200 m².");

        assertThat(analysis.lotAreaSquareMeters()).isNull();
        assertThat(analysis.occupancyIndex()).isNull();
        assertThat(analysis.observations()).anyMatch(message -> message.contains("separadores ambiguos"));
    }

    @Test
    void extractsFirstFloorOccupationWhenAreaPrecedesItsLabel() {
        ProjectAnalysisResponse analysis = service.analyze(
                "Quiero construir una vivienda unifamiliar de dos pisos en un lote urbano de 180 m\u00b2, "
                + "con 90 m\u00b2 de ocupaci\u00f3n en primer piso y 180 m\u00b2 construidos en total.");

        assertThat(analysis.lotAreaSquareMeters()).isEqualTo(180.0);
        assertThat(analysis.occupiedAreaSquareMeters()).isEqualTo(90.0);
        assertThat(analysis.totalBuiltAreaSquareMeters()).isEqualTo(180.0);
        assertThat(analysis.occupancyIndex()).isEqualByComparingTo("0.5");
        assertThat(analysis.constructionIndex()).isEqualByComparingTo("1");
    }
}
