package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

class BraveOfficialSourceSearchProviderTest {

    private final BraveOfficialSourceSearchProvider provider =
            new BraveOfficialSourceSearchProvider(WebClient.builder(), "test-key");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retainsOnlyDistinctHttpsGovernmentRegulationResults() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "web": { "results": [
                    {"title":"POT oficial de Villa Nueva","url":"https://villanueva.gov.co/pot.pdf","description":"Plan de ordenamiento territorial"},
                    {"title":"POT oficial de Villa Nueva","url":"https://villanueva.gov.co/pot.pdf","description":"Plan de ordenamiento territorial"},
                    {"title":"POT mirror","url":"https://example.com/pot.pdf","description":"Plan de ordenamiento territorial"},
                    {"title":"POT inseguro","url":"http://villanueva.gov.co/pot.pdf","description":"Plan de ordenamiento territorial"},
                    {"title":"Portal de servicios","url":"https://villanueva.gov.co/servicios","description":"Trámites generales"},
                    {"title":"POT válido","url":"https://planeacion.villanueva.gov.co/acuerdo","description":"Acuerdo municipal"}
                  ] }
                }
                """);

        List<?> candidates = provider.toOfficialCandidates(payload);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).allSatisfy(candidate ->
                assertThat(((com.normbuild.regulation.dto.OfficialSourceCandidateResponse) candidate).reviewStatus())
                        .isEqualTo("REQUIRES_LEGAL_REVIEW"));
    }

    @Test
    void rejectsUrlsOutsideAnOfficialColombianGovernmentHost() {
        assertThat(provider.officialHost("https://planning.example.gov.co/pot")).isEqualTo("planning.example.gov.co");
        assertThat(provider.officialHost("http://planning.example.gov.co/pot")).isEmpty();
        assertThat(provider.officialHost("https://notgov.co/pot")).isEmpty();
        assertThat(provider.officialHost("javascript:alert(1)")).isEmpty();
    }

    @Test
    void givesAnActionableMessageWhenBraveRejectsTheSearchKey() {
        var error = WebClientResponseException.create(403, "Forbidden", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(provider.mapProviderError(error).getReason())
                .contains("Brave rechazó la clave", "plan Search API");
    }

    @Test
    void explainsWhenBraveRejectsSearchParameters() {
        var error = WebClientResponseException.create(422, "Unprocessable Entity", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(provider.mapProviderError(error).getReason())
                .contains("Brave rechazó los parámetros");
    }

    @Test
    void sendsSimpleCountryAndLanguageFilteredQueryToBrave() {
        AtomicReference<java.net.URI> requestUri = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            requestUri.set(request.url());
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"web\":{\"results\":[]}}")
                    .build());
        });
        var searchProvider = new BraveOfficialSourceSearchProvider(client, "test-key");

        searchProvider.search("Manizales").join();

        String query = URLDecoder.decode(requestUri.get().getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).isEqualTo("q=site:gov.co Manizales Colombia POT plan de ordenamiento territorial normativa urbana")
                .doesNotContain("country=", "search_lang=", "ui_lang", " OR ", "(");
    }
}
