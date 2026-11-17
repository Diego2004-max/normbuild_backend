package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectFactExtractorTest {

    private final ProjectFactExtractor extractor = new ProjectFactExtractor();

    @ParameterizedTest
    @CsvSource({"un piso,1", "dos pisos,2", "tres niveles,3", "10 pisos,10", "DOS PISOS,2"})
    void recognizesWrittenAndNumericFloorCounts(String description, int expectedFloors) {
        assertThat(extractor.extract(description).floors()).contains(expectedFloors);
    }

    @Test
    void recognizesOriginalResidentialProject() {
        ProjectFacts facts = extractor.extract("Casa de dos pisos con una altura total de 7.5 metros en un predio de 180 metros cuadrados, con retiros e índice de ocupación.");

        assertThat(facts.floors()).contains(2);
        assertThat(facts.heightMeters()).contains(7.5);
        assertThat(facts.areaSquareMeters()).contains(180.0);
        assertThat(facts.mentionsResidentialUse()).isTrue();
        assertThat(facts.mentionsSetbacks()).isTrue();
        assertThat(facts.mentionsOccupancyIndex()).isTrue();
    }

    @Test
    void doesNotTreatLotAreaOrSetbackAsBuildingHeight() {
        ProjectFacts facts = extractor.extract("Lote de 180 metros cuadrados, retiro posterior de 3 metros y vivienda de dos pisos.");

        assertThat(facts.heightMeters()).isEmpty();
        assertThat(facts.areaSquareMeters()).contains(180.0);
    }

    @Test
    void recognizesCommaDecimalsAndTrailingHeightLabel() {
        ProjectFacts facts = extractor.extract("Lote de 120 m², casa de tres pisos y 8,5 m de altura.");

        assertThat(facts.floors()).contains(3);
        assertThat(facts.heightMeters()).contains(8.5);
        assertThat(facts.areaSquareMeters()).contains(120.0);
    }

    @Test
    void doesNotInventMissingMeasurements() {
        ProjectFacts facts = extractor.extract(null);

        assertThat(facts.floors()).isEmpty();
        assertThat(facts.heightMeters()).isEmpty();
        assertThat(facts.areaSquareMeters()).isEmpty();
    }

    @Test
    void recognizesLotAreaInNaturalProjectDescription() {
        ProjectFacts facts = extractor.extract("El predio cuenta con un área total de 180 metros cuadrados y requiere retiros.");

        assertThat(facts.lotAreaSquareMeters()).contains(180.0);
        assertThat(facts.totalBuiltAreaSquareMeters()).isEmpty();
    }

    @Test
    void recognizesCoveredFirstFloorFootprint() {
        ProjectFacts facts = extractor.extract(
                "Lote urbano de 180 m². La huella cubierta del primer piso será de 90 m² y el área construida total será de 180 m².");

        assertThat(facts.lotAreaSquareMeters()).contains(180.0);
        assertThat(facts.occupiedAreaSquareMeters()).contains(90.0);
        assertThat(facts.totalBuiltAreaSquareMeters()).contains(180.0);
    }
}
