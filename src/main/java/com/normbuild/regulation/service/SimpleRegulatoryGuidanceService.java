package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.CitationSourceType;
import com.normbuild.regulation.dto.CitedRegulationResponse;
import com.normbuild.regulation.dto.ChecklistGenerationMode;
import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.ProjectAnalysisResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SimpleRegulatoryGuidanceService {

    private static final String NATIONAL_DECREE_URL = "https://minvivienda.gov.co/sites/default/files/normativa/decreto-unico-vivienda-actualizado-ultima-actualizacion-16022026_0_1.pdf";
    private static final Pattern PERMIT_QUESTION = Pattern.compile(
            "(?iu)(?:sin\\s+(?:permisos?|licencia)|(?:necesito|requiere|requiero|debo sacar)\\s+(?:un\\s+)?(?:permiso|licencia))");

    public Optional<ComplianceChecklistResponse> answer(
            ComplianceQueryRequest request, ProjectAnalysisResponse analysis) {
        String description = request.projectDescription().toLowerCase(Locale.ROOT);
        if (!PERMIT_QUESTION.matcher(description).find()) {
            return Optional.empty();
        }

        CitedRegulationResponse citation = new CitedRegulationResponse(
                UUID.nameUUIDFromBytes(NATIONAL_DECREE_URL.getBytes(StandardCharsets.UTF_8)),
                "Decreto Único Reglamentario del Sector Vivienda",
                "Decreto 1077 de 2015",
                "Artículo 2.2.6.1.1.7 - Licencia de construcción",
                NATIONAL_DECREE_URL,
                1.0,
                "La modalidad de obra nueva es la autorización para adelantar edificaciones en terrenos no construidos. La autoridad competente debe confirmar la modalidad y cualquier excepción aplicable.",
                CitationSourceType.OFFICIAL_BASELINE);
        String answer = "Respuesta corta: No. No inicies la construcción de una vivienda nueva sin obtener previamente la licencia de construcción aplicable [1].\n\n"
                + "Por qué: el Decreto 1077 de 2015 define la licencia de obra nueva como la autorización para construir una edificación en un terreno no construido [1]. Puede haber excepciones legales específicas, pero no deben suponerse sin revisión oficial.\n\n"
                + "Qué hacer: solicita la ficha o concepto urbanístico del predio y confirma ante la curaduría urbana o la Secretaría de Planeación de "
                + request.jurisdiction() + " la modalidad, los documentos y la vigencia exigida antes de comenzar.\n\n"
                + "Riesgo: Por verificar.";
        return Optional.of(new ComplianceChecklistResponse(answer, "Por verificar", List.of(citation),
                OffsetDateTime.now(), ChecklistGenerationMode.VERIFIED_GUIDANCE, analysis));
    }
}
