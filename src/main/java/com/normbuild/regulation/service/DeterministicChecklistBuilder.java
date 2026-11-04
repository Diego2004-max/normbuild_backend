package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.repository.RegulatoryDocumentProjection;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DeterministicChecklistBuilder {

    private final ProjectFactExtractor factExtractor;

    public DeterministicChecklistBuilder(ProjectFactExtractor factExtractor) {
        this.factExtractor = factExtractor;
    }

    public String build(ComplianceQueryRequest request, List<RegulatoryDocumentProjection> documents) {
        ProjectFacts facts = factExtractor.extract(request.projectDescription());
        StringBuilder builder = new StringBuilder();
        builder.append("Checklist preliminar de cumplimiento para ").append(request.jurisdiction()).append("\n\n");
        builder.append("1. Datos detectados del proyecto\n");
        builder.append("- Uso: ").append(facts.mentionsResidentialUse() ? "vivienda residencial." : "debe confirmarse el uso exacto del proyecto.").append("\n");
        builder.append("- Número de pisos: ").append(facts.floors().map(Object::toString).orElse("no indicado")).append(".\n");
        builder.append("- Altura propuesta: ").append(facts.heightMeters().map(this::formatMeters).orElse("no indicada")).append(".\n");
        builder.append("- Área del predio o construcción: ").append(facts.areaSquareMeters().map(this::formatSquareMeters).orElse("no indicada")).append(".\n\n");

        builder.append("2. Normas que debes revisar antes de diseñar o radicar\n");
        appendRule(builder, "Uso del suelo y tratamiento urbanístico", "Confirma la compatibilidad del uso propuesto y el tratamiento urbanístico en el instrumento de ordenamiento de " + request.jurisdiction() + ".");
        appendRule(builder, "Edificabilidad", "Valida índice de ocupación, índice de construcción, altura máxima, número de pisos y volumetría permitida para el sector.");
        appendRule(builder, "Retiros y aislamientos", facts.mentionsSetbacks()
                ? "Tu descripción menciona retiros o aislamientos; debes comprobar aislamiento posterior, lateral, antejardín y empates con colindantes."
                : "Debes confirmar si el predio exige aislamiento posterior, aislamiento lateral, antejardín o retrocesos.");
        appendRule(builder, "Licencia urbanística", "Antes de construir necesitas verificar el trámite de licencia de construcción y sus documentos técnicos.");
        appendRule(builder, "Seguridad estructural", "El diseño debe cumplir NSR-10 con memoria de cálculo, planos estructurales y revisión de cimentación.");
        builder.append("\n");

        builder.append("3. Lectura rápida con los datos ingresados\n");
        appendFactAssessment(builder, facts);
        builder.append("\n");

        builder.append("4. Evidencias mínimas para continuar\n");
        builder.append("- Certificado de tradición y libertad del predio.\n");
        builder.append("- Plano de localización, cuadro de áreas y levantamiento del lote.\n");
        builder.append("- Consulta de norma urbana o ficha normativa aplicable al predio.\n");
        builder.append("- Planos arquitectónicos con áreas, altura, retiros, aislamientos y volumetría.\n");
        builder.append("- Memoria estructural y planos estructurales conforme a NSR-10.\n");
        builder.append("- Validación de servicios públicos, accesibilidad y afectaciones urbanísticas.\n\n");

        builder.append("5. Fuentes recuperadas por similitud semántica\n");
        for (RegulatoryDocumentProjection document : documents) {
            builder.append("- ")
                    .append(document.getRegulationCode())
                    .append(" / ")
                    .append(document.getArticleReference())
                    .append(": ")
                    .append(document.getTitle())
                    .append(".\n");
        }
        builder.append("\nNivel de riesgo: Por verificar. La similitud de las fuentes no determina el cumplimiento. Falta contrastar el proyecto con la norma específica del predio; los índices y aislamientos dependen de la ubicación, el tratamiento urbanístico y las condiciones de colindancia.");
        return builder.toString();
    }

    private void appendRule(StringBuilder builder, String title, String detail) {
        builder.append("- ").append(title).append(": ").append(detail).append("\n");
    }

    private void appendFactAssessment(StringBuilder builder, ProjectFacts facts) {
        facts.floors().ifPresentOrElse(
                floors -> builder.append("- Los ").append(floors).append(" pisos deben compararse con la altura máxima permitida en la ficha normativa del sector.\n"),
                () -> builder.append("- Falta indicar el número de pisos para evaluar altura y volumetría.\n")
        );
        facts.heightMeters().ifPresentOrElse(
                height -> builder.append("- La altura de ").append(formatMeters(height)).append(" debe cruzarse con número máximo de pisos, empates y aislamientos exigidos.\n"),
                () -> builder.append("- Falta indicar altura total para validar volumetría.\n")
        );
        facts.areaSquareMeters().ifPresentOrElse(
                area -> builder.append("- El área de ").append(formatSquareMeters(area)).append(" sirve para calcular ocupación, construcción máxima y área libre exigida.\n"),
                () -> builder.append("- Falta indicar área del lote o área construida para calcular aprovechamiento.\n")
        );
        if (facts.mentionsOccupancyIndex()) {
            builder.append("- Como preguntas por índice de ocupación, debes comparar área ocupada en primer piso contra el área del predio.\n");
        }
        if (facts.mentionsVolumetry()) {
            builder.append("- Como preguntas por volumetría, revisa altura, aislamientos, retrocesos y relación con predios vecinos.\n");
        }
    }

    private String formatMeters(double value) {
        return formatNumber(value) + " m";
    }

    private String formatSquareMeters(double value) {
        return formatNumber(value) + " m²";
    }

    private String formatNumber(double value) {
        return new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
