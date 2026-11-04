package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import com.normbuild.regulation.dto.CitedRegulationResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RegulatoryAnswerSanitizer {

    private static final Pattern UNSUPPORTED_NUMERIC_LIMIT = Pattern.compile(
            "(?iu)(?:m[aá]xim[oa]|permitid[oa])[^.!?\\n]*\\d|\\d[^.!?\\n]*(?:m[aá]xim[oa]|permitid[oa])");

    public String sanitize(String generatedAnswer, ProjectAnalysisResponse analysis,
                          List<CitedRegulationResponse> citations) {
        List<String> safeLines = new ArrayList<>();
        for (String line : generatedAnswer.lines().toList()) {
            if (!UNSUPPORTED_NUMERIC_LIMIT.matcher(line).find() && !line.isBlank()) {
                String plainText = line.strip().replace("**", "");
                if (plainText.startsWith("* ")) {
                    plainText = plainText.substring(2);
                }
                safeLines.add(plainText);
            }
        }

        List<String> sections = new ArrayList<>();
        String proposedRatios = formatProposedRatios(analysis);
        if (!proposedRatios.isBlank()) {
            sections.add(proposedRatios);
        }
        String safeChecklist = String.join("\n", safeLines);
        if (!safeChecklist.isBlank()) {
            sections.add(safeChecklist);
        }
        sections.add(buildVerificationChecklist(citations));
        String conclusion = "Los máximos legales aún no se pueden confirmar. Hace falta la ficha normativa vigente del predio con su tratamiento urbanístico, uso permitido y tipología (adosada o aislada).";
        if (!safeChecklist.toLowerCase(Locale.ROOT).contains("riesgo: por verificar")) {
            conclusion += " Riesgo: Por verificar.";
        }
        sections.add(conclusion);
        return String.join("\n\n", sections);
    }

    private String buildVerificationChecklist(List<CitedRegulationResponse> citations) {
        List<String> checks = new ArrayList<>();
        for (int index = 0; index < citations.size(); index++) {
            String reference = citations.get(index).articleReference();
            int citationNumber = index + 1;
            if (reference != null && reference.matches("(?iu).*\\b250\\b.*")) {
                checks.add("1. Confirmar en la ficha del predio el tipo de edificabilidad y el código morfológico de altura; el artículo 250 distingue tipología y pisos [" + citationNumber + "].");
            }
            if (reference != null && reference.matches("(?iu).*\\b267\\b.*")) {
                checks.add("2. Si la edificación es aislada, verificar los retiros y aislamientos exigidos para las dimensiones del lote [" + citationNumber + "].");
            }
        }
        if (checks.isEmpty()) {
            checks.add("1. Obtener la ficha normativa oficial del predio para conocer tratamiento, uso, edificabilidad y altura aplicables.");
            checks.add("2. Confirmar retiros, aislamientos y condiciones del lote con Planeación municipal o la curaduría competente.");
        }
        return "Verificaciones antes de radicar:\n" + String.join("\n", checks);
    }

    private String formatProposedRatios(ProjectAnalysisResponse analysis) {
        List<String> ratios = new ArrayList<>();
        if (analysis.lotAreaSquareMeters() != null && analysis.lotAreaSquareMeters() > 0
                && analysis.occupiedAreaSquareMeters() != null && analysis.occupancyIndex() != null) {
            ratios.add("ocupación " + formatArea(analysis.occupiedAreaSquareMeters()) + " / "
                    + formatArea(analysis.lotAreaSquareMeters()) + " m² = "
                    + formatRatio(analysis.occupancyIndex()) + " (" + formatPercent(analysis.occupancyIndex()) + ")");
        }
        if (analysis.lotAreaSquareMeters() != null && analysis.lotAreaSquareMeters() > 0
                && analysis.totalBuiltAreaSquareMeters() != null && analysis.constructionIndex() != null) {
            ratios.add("construcción " + formatArea(analysis.totalBuiltAreaSquareMeters()) + " / "
                    + formatArea(analysis.lotAreaSquareMeters()) + " m² = "
                    + formatRatio(analysis.constructionIndex()) + " (" + formatPercent(analysis.constructionIndex()) + ")");
        }
        if (ratios.isEmpty()) {
            return "";
        }
        return "Cálculos de la propuesta; no son límites normativos: " + String.join("; ", ratios) + ".";
    }

    private String formatArea(Double value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formatter.setGroupingUsed(false);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(value);
    }

    private String formatRatio(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formatter.setGroupingUsed(false);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(value);
    }

    private String formatPercent(BigDecimal ratio) {
        return formatArea(ratio.multiply(BigDecimal.valueOf(100)).doubleValue()) + "%";
    }
}
