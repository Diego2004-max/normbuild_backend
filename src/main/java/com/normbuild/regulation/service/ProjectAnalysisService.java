package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProjectAnalysisService {

    private final ProjectFactExtractor factExtractor;

    public ProjectAnalysisService(ProjectFactExtractor factExtractor) {
        this.factExtractor = factExtractor;
    }

    public ProjectAnalysisResponse analyze(String description) {
        ProjectFacts facts = factExtractor.extract(description);
        Double lot = facts.lotAreaSquareMeters().orElse(null);
        Double occupied = facts.occupiedAreaSquareMeters().orElse(null);
        Double built = facts.totalBuiltAreaSquareMeters().orElse(null);
        List<String> observations = new ArrayList<>();
        if (description != null && description.matches("(?s).*\\b[1-9]\\d{0,2}[.,]\\d{3}\\s*(?:m²|m2|metros?\\s+cuadrados).*")) {
            observations.add("Hay áreas con separadores ambiguos. Escribe 1200 m² para mil doscientos, o usa una coma decimal sin separador de miles.");
        }
        if (lot == null) {
            observations.add("Indica una única área del lote para calcular los índices; no se infiere del área construida.");
        } else if (lot <= 0) {
            observations.add("El área del lote debe ser mayor que cero.");
        }
        if (occupied == null) {
            observations.add("Indica el área ocupada bajo cubierta en el primer piso para calcular la ocupación.");
        } else if (occupied < 0) {
            observations.add("El área ocupada no puede ser negativa.");
        }
        if (built == null) {
            observations.add("Indica el área construida total; no se obtiene multiplicando los pisos por el área del lote.");
        } else if (built < 0) {
            observations.add("El área construida no puede ser negativa.");
        }
        boolean validLot = lot != null && lot > 0;
        BigDecimal occupancy = ratio(occupied, lot);
        BigDecimal construction = ratio(built, lot);
        BigDecimal unoccupied = null;
        if (validLot && occupied != null && occupied >= 0) {
            if (occupied <= lot) {
                unoccupied = BigDecimal.valueOf(lot).subtract(BigDecimal.valueOf(occupied));
            } else {
                observations.add("La ocupación propuesta supera el área del lote; revisa el cuadro de áreas.");
            }
        }
        if (built != null && occupied != null && built >= 0 && occupied >= 0 && built < occupied) {
            observations.add("El área construida total es menor que el primer piso; revisa los datos ingresados.");
        }
        observations.add("Estos cocientes describen tu propuesta, no los máximos permitidos. La norma del predio debe confirmar las áreas computables, exclusiones y base de cálculo.");
        observations.add("Para saber si puedes construir así, falta identificar el predio, su tratamiento urbanístico, uso permitido, alturas y aislamientos aplicables.");
        return new ProjectAnalysisResponse(lot, occupied, built, occupancy, construction, unoccupied, List.copyOf(observations));
    }

    private BigDecimal ratio(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || numerator < 0 || denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
