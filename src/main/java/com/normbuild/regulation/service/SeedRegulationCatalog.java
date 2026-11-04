package com.normbuild.regulation.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SeedRegulationCatalog {

    public List<SeedRegulationDocument> bogotaDocuments() {
        return List.of(
                SeedRegulationDocument.from(
                        "Plan de Ordenamiento Territorial de Bogotá",
                        "Bogotá D.C.",
                        "Decreto Distrital 555 de 2021",
                        "Artículos 255 y 260 - tratamientos urbanísticos y altura",
                        """
                        Para construir una casa o vivienda en suelo urbano de Bogotá, la revisión normativa debe iniciar con la identificación del tratamiento urbanístico del predio, el área de actividad, el uso permitido y las condiciones de altura. El tratamiento urbanístico define cómo se aplican las normas urbanas del sector. La altura máxima se revisa con la ficha normativa y los mapas aplicables, porque puede medirse en número de pisos y depende de localización, tratamiento y condiciones específicas del lote.
                        """
                ),
                SeedRegulationDocument.from(
                        "Plan de Ordenamiento Territorial de Bogotá",
                        "Bogotá D.C.",
                        "Decreto Distrital 555 de 2021",
                        "Artículos 267 y 310 - edificabilidad, ocupación y cargas urbanísticas",
                        """
                        La evaluación de edificabilidad para vivienda debe contrastar el área del lote o predio, el área ocupada en primer piso, el área total construida, el índice de ocupación, el índice de construcción, la altura máxima permitida y la volumetría autorizada por la norma urbana del sector. Si el proyecto tiene 180 metros cuadrados, dos pisos o una altura cercana a 7.5 metros, esos datos sirven para calcular aprovechamiento, área libre, ocupación máxima y cumplimiento de cargas urbanísticas cuando aplique edificabilidad adicional.
                        """
                ),
                SeedRegulationDocument.from(
                        "Plan de Ordenamiento Territorial de Bogotá",
                        "Bogotá D.C.",
                        "Decreto Distrital 555 de 2021",
                        "Manual de normas comunes - aislamientos, retiros y empates",
                        """
                        Los retiros, aislamientos posteriores y laterales, antejardines, retrocesos y empates volumétricos deben revisarse antes de radicar una licencia de construcción. En proyectos residenciales unifamiliares o bifamiliares, el cumplimiento depende de la localización del predio, el tratamiento urbanístico, el tipo de edificación, la altura propuesta, el frente del lote y las condiciones de colindancia. Si la norma específica exige aislamiento posterior o lateral, el plano arquitectónico debe demostrar distancia libre, continuidad y área no ocupada.
                        """
                ),
                SeedRegulationDocument.from(
                        "Reglamento Colombiano de Construcción Sismo Resistente",
                        "Bogotá D.C.",
                        "NSR-10",
                        "Títulos A y E - seguridad estructural para vivienda",
                        """
                        Toda edificación nueva destinada a vivienda debe cumplir los requisitos del Reglamento Colombiano de Construcción Sismo Resistente. Para una casa de uno o dos pisos, el solicitante debe contar con diseño estructural, memoria de cálculo, planos estructurales y revisión de cargas, cimentación, muros, columnas, vigas y elementos de resistencia sísmica. El cumplimiento urbanístico sobre metros cuadrados, altura o retiros no reemplaza la obligación de acreditar seguridad estructural dentro del trámite de licencia.
                        """
                ),
                SeedRegulationDocument.from(
                        "Régimen nacional de licencias urbanísticas",
                        "Bogotá D.C.",
                        "Decreto Único Reglamentario 1077 de 2015",
                        "Licencia de construcción y documentos técnicos",
                        """
                        La licencia de construcción autoriza desarrollar obras de edificación cuando el proyecto acredita cumplimiento urbanístico, arquitectónico y técnico. Para vivienda residencial se deben presentar planos arquitectónicos, estudios y diseños técnicos, identificación del predio, titularidad o autorización correspondiente, cuadro de áreas, localización y documentación exigida por la autoridad competente. La autoridad revisa uso permitido, aprovechamiento del predio, volumetría, accesibilidad, seguridad, estabilidad y compatibilidad normativa.
                        """
                )
        );
    }
}
