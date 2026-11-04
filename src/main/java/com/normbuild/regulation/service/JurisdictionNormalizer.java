package com.normbuild.regulation.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class JurisdictionNormalizer {

    private static final Map<String, String> CANONICAL_NAMES = Map.ofEntries(
            Map.entry("bogota", "Bogotá D.C."),
            Map.entry("bogota dc", "Bogotá D.C."),
            Map.entry("bogota d c", "Bogotá D.C."),
            Map.entry("bogota d.c.", "Bogotá D.C."),
            Map.entry("bogota d.c", "Bogotá D.C."),
            Map.entry("pasto", "Pasto"),
            Map.entry("san juan de pasto", "Pasto"),
            Map.entry("medellin", "Medellín"),
            Map.entry("cali", "Cali"),
            Map.entry("santiago de cali", "Cali"),
            Map.entry("bucaramanga", "Bucaramanga"),
            Map.entry("barranquilla", "Barranquilla"),
            Map.entry("cartagena", "Cartagena"),
            Map.entry("cartagena de indias", "Cartagena")
    );

    private JurisdictionNormalizer() {
    }

    public static String canonicalize(String jurisdiction) {
        if (jurisdiction == null || jurisdiction.isBlank()) {
            throw new IllegalArgumentException("La jurisdicción es obligatoria.");
        }
        String trimmed = jurisdiction.strip().replaceAll("\\s+", " ");
        String lookupKey = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return CANONICAL_NAMES.getOrDefault(lookupKey, trimmed);
    }
}
