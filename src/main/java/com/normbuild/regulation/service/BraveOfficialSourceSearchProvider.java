package com.normbuild.regulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.normbuild.regulation.dto.OfficialSourceCandidateResponse;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BraveOfficialSourceSearchProvider implements OfficialSourceSearchProvider {

    private static final String SEARCH_ENDPOINT = "https://api.search.brave.com/res/v1/web/search";
    private static final int RESULT_LIMIT = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger(BraveOfficialSourceSearchProvider.class);
    private static final Pattern REGULATORY_EVIDENCE = Pattern.compile(
            "(?iu)\\b(?:pot|ordenamiento\\s+territorial|normativa\\s+urban[ií]stica|"
                    + "acuerdo\\s+municipal|decreto\\s+distrital|plan\\s+de\\s+ordenamiento|planeaci[oó]n)\\b");
    private final WebClient webClient;
    private final String apiKey;

    public BraveOfficialSourceSearchProvider(
            WebClient.Builder webClientBuilder,
            @Value("${normbuild.search.brave-api-key:}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(SEARCH_ENDPOINT).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public CompletableFuture<List<OfficialSourceCandidateResponse>> search(String municipality) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La búsqueda web de fuentes oficiales no está configurada en el servidor.");
        }

        String query = "site:gov.co " + municipality.strip()
                + " Colombia POT plan de ordenamiento territorial normativa urbana";
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("q", query).build())
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(12))
                .map(this::toOfficialCandidates)
                .onErrorMap(WebClientResponseException.class, this::mapProviderError)
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "El proveedor de búsqueda no respondió. Intenta de nuevo más tarde.", error))
                .toFuture();
    }

    List<OfficialSourceCandidateResponse> toOfficialCandidates(JsonNode payload) {
        JsonNode results = payload.path("web").path("results");
        Map<String, OfficialSourceCandidateResponse> candidates = new LinkedHashMap<>();
        if (!results.isArray()) {
            return List.of();
        }
        for (JsonNode result : results) {
            String url = result.path("url").asText("").strip();
            String host = officialHost(url);
            if (host.isBlank() || !isRegulatoryResult(result)) {
                continue;
            }
            candidates.putIfAbsent(url, new OfficialSourceCandidateResponse(
                    result.path("title").asText("Fuente oficial"), url,
                    result.path("description").asText(""), host, "REQUIRES_LEGAL_REVIEW"));
            if (candidates.size() == RESULT_LIMIT) {
                break;
            }
        }
        return List.copyOf(candidates.values());
    }

    String officialHost(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !host.toLowerCase(Locale.ROOT).matches("(?:[a-z0-9-]+\\.)+gov\\.co")) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    boolean isRegulatoryResult(JsonNode result) {
        String evidence = (result.path("title").asText("") + " "
                + result.path("description").asText("")).toLowerCase(Locale.ROOT);
        return REGULATORY_EVIDENCE.matcher(evidence).find();
    }

    ResponseStatusException mapProviderError(WebClientResponseException exception) {
        int providerStatus = exception.getStatusCode().value();
        LOGGER.warn("Brave Search API returned HTTP {} for official-source discovery", providerStatus);
        HttpStatus status = providerStatus == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        String message = switch (providerStatus) {
            case 401, 403 -> "Brave rechazó la clave de búsqueda. Verifica que copiaste una clave activa del plan Search API.";
            case 422 -> "Brave rechazó los parámetros de búsqueda. Revisa la configuración del servicio.";
            case 429 -> "La búsqueda oficial alcanzó su límite temporal. Espera un momento e inténtalo de nuevo.";
            default -> "El proveedor de búsqueda respondió con un error HTTP " + providerStatus + ". Intenta de nuevo más tarde.";
        };
        return new ResponseStatusException(status, message, exception);
    }
}
