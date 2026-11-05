package com.normbuild.regulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BraveRegulatoryContextProvider {

    private static final String CONTEXT_ENDPOINT = "https://api.search.brave.com/res/v1/llm/context";
    private static final int MAX_SOURCE_COUNT = 6;
    private static final int MAX_SNIPPET_LENGTH = 2400;
    private final WebClient webClient;
    private final String apiKey;

    public BraveRegulatoryContextProvider(
            WebClient.Builder webClientBuilder,
            @Value("${normbuild.search.brave-api-key:}") String apiKey) {
        this.webClient = webClientBuilder.clone().baseUrl(CONTEXT_ENDPOINT).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public CompletableFuture<List<OfficialWebContextDocument>> search(String municipality) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La investigación web de normativa no está configurada en el servidor.");
        }
        String query = "site:gov.co " + municipality.strip()
                + " Colombia POT plan de ordenamiento territorial normativa urbana construcción "
                + "uso del suelo edificabilidad alturas aislamientos licencia urbanística";
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("q", query).build())
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(18))
                .map(this::parseContext)
                .onErrorMap(WebClientResponseException.class, this::mapProviderError)
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "La búsqueda oficial no respondió. Intenta nuevamente más tarde.", error))
                .toFuture();
    }

    List<OfficialWebContextDocument> parseContext(JsonNode payload) {
        JsonNode generic = payload.path("grounding").path("generic");
        if (!generic.isArray()) {
            return List.of();
        }
        List<OfficialWebContextDocument> documents = new ArrayList<>();
        for (JsonNode item : generic) {
            String url = item.path("url").asText("").strip();
            String hostname = officialHost(url);
            JsonNode snippetsNode = item.path("snippets");
            if (hostname.isBlank() || !snippetsNode.isArray()) {
                continue;
            }
            List<String> snippets = new ArrayList<>();
            for (JsonNode snippet : snippetsNode) {
                String text = snippet.asText("").strip();
                if (!text.isBlank()) {
                    snippets.add(text.length() > MAX_SNIPPET_LENGTH ? text.substring(0, MAX_SNIPPET_LENGTH) : text);
                }
            }
            if (snippets.isEmpty()) {
                continue;
            }
            documents.add(new OfficialWebContextDocument(
                    item.path("title").asText("Fuente oficial colombiana"), url, hostname, List.copyOf(snippets)));
            if (documents.size() == MAX_SOURCE_COUNT) {
                break;
            }
        }
        return List.copyOf(documents);
    }

    String officialHost(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443
                    || !host.toLowerCase(Locale.ROOT).matches("(?:[a-z0-9-]+\\.)+gov\\.co")) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    ResponseStatusException mapProviderError(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        HttpStatus mappedStatus = status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        String message = switch (status) {
            case 401, 403 -> "Brave rechazó la clave de búsqueda. Verifica la clave del servidor.";
            case 422 -> "Brave rechazó la consulta de investigación normativa.";
            case 429 -> "La investigación alcanzó el límite temporal del proveedor. Intenta más tarde.";
            default -> "La búsqueda oficial respondió con un error HTTP " + status + ".";
        };
        return new ResponseStatusException(mappedStatus, message, exception);
    }
}
