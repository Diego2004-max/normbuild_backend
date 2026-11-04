package com.normbuild.regulation.service;

import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProjectFactExtractor {

    private static final Map<String, Integer> FLOOR_NUMBERS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("uno", 1), Map.entry("dos", 2), Map.entry("tres", 3),
            Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6),
            Map.entry("siete", 7), Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10));
    private static final Pattern FLOORS_PATTERN = Pattern.compile(
            "\\b(\\d{1,3}|un|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\s+(?:pisos?|niveles?)\\b");
    private static final String LENGTH_VALUE = "(\\d+(?:[.,]\\d+)?)\\s*(?:metros?|m)\\b(?!\\s*(?:cuadrados?\\b|[2²]))";
    private static final Pattern HEIGHT_PATTERN = Pattern.compile(
            "\\baltura(?:\\s+total)?(?:\\s+propuesta)?(?:\\s+(?:de|es))?\\s*[:=]?\\s*" + LENGTH_VALUE);
    private static final Pattern TRAILING_HEIGHT_PATTERN = Pattern.compile(
            "\\b" + LENGTH_VALUE + "\\s+de\\s+(?:altura|alto)\\b");
    private static final Pattern AREA_PATTERN = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*(metros?\\s*cuadrados|m2|m²)", Pattern.CASE_INSENSITIVE);
    private static final String AREA_VALUE = "(-?\\d+(?:[.,]\\d+)?)\\s*(?:metros?\\s*cuadrados|m²|m2)(?![\\p{L}\\d])";
    private static final String CONNECTING_WORDS = "(?:\\s+[\\p{L}]+){0,4}?\\s*[:=]?\\s*";
    private static final Pattern LOT_AREA_PATTERN = Pattern.compile(
            "\\b(?:lote|predio|terreno|parcela)" + CONNECTING_WORDS + AREA_VALUE);
    private static final Pattern OCCUPIED_AREA_PATTERN = Pattern.compile(
            "\\b(?:ocupar|ocupaci[oó]n|[aá]rea\\s+ocupada)" + CONNECTING_WORDS + AREA_VALUE, Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern FIRST_FLOOR_AREA_PATTERN = Pattern.compile(
            AREA_VALUE + "\\s+en\\s+(?:el\\s+)?primer\\s+piso\\b");
    private static final Pattern BUILT_AREA_PATTERN = Pattern.compile(
            "\\b(?:[aá]rea(?:\\s+total)?\\s+construida|construcci[oó]n\\s+total)" + CONNECTING_WORDS + AREA_VALUE, Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TOTAL_AREA_PATTERN = Pattern.compile(
            AREA_VALUE + "\\s+(?:en\\s+total|totales?|construidos?(?:\\s+en\\s+total)?)\\b");

    public ProjectFacts extract(String description) {
        String normalized = description == null ? "" : description.toLowerCase(Locale.ROOT);
        Optional<Double> height = extractDecimal(HEIGHT_PATTERN, normalized)
                .or(() -> extractDecimal(TRAILING_HEIGHT_PATTERN, normalized));
        return new ProjectFacts(
                extractInteger(FLOORS_PATTERN, normalized),
                height,
                extractDecimal(AREA_PATTERN, normalized),
                extractUniqueArea(normalized, LOT_AREA_PATTERN),
                extractUniqueArea(normalized, OCCUPIED_AREA_PATTERN, FIRST_FLOOR_AREA_PATTERN),
                extractUniqueArea(normalized, BUILT_AREA_PATTERN, TOTAL_AREA_PATTERN),
                containsAny(normalized, "residencial", "vivienda", "casa", "bifamiliar", "unifamiliar"),
                containsAny(normalized, "retiro", "retiros", "aislamiento", "aislamientos", "antejardín", "antejardin"),
                containsAny(normalized, "ocupación", "ocupacion", "índice de ocupación", "indice de ocupacion"),
                containsAny(normalized, "volumetría", "volumetria", "altura", "pisos")
        );
    }

    private Optional<Double> extractUniqueArea(String description, Pattern... patterns) {
        Set<Double> values = new LinkedHashSet<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(description);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (token.matches("-?[1-9]\\d{0,2}[.,]\\d{3}")) {
                    return Optional.empty();
                }
                values.add(Double.parseDouble(token.replace(',', '.')));
            }
        }
        return values.size() == 1 ? Optional.of(values.iterator().next()) : Optional.empty();
    }

    private Optional<Integer> extractInteger(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String valueText = matcher.group(1);
        return Optional.of(FLOOR_NUMBERS.containsKey(valueText)
                ? FLOOR_NUMBERS.get(valueText) : Integer.parseInt(valueText));
    }

    private Optional<Double> extractDecimal(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
