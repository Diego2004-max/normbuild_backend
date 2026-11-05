package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class BraveRegulatoryContextProviderTest {

    private final BraveRegulatoryContextProvider provider =
            new BraveRegulatoryContextProvider(WebClient.builder(), "test-key");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesOnlyHttpsColombianGovernmentContextAndCapsResults() throws Exception {
        var payload = objectMapper.readTree("""
                {"grounding":{"generic":[
                  {"url":"https://planeacion.gov.co/pot","title":"POT oficial","snippets":["Texto normativo consultado."]},
                  {"url":"https://example.com/pot","title":"Mirror","snippets":["Texto no oficial."]},
                  {"url":"http://municipio.gov.co/pot","title":"Inseguro","snippets":["Texto inseguro."]},
                  {"url":"https://gov.co/pot","title":"Dominio inválido","snippets":["Texto no aceptado."]}
                ]}}
                """);

        List<OfficialWebContextDocument> documents = provider.parseContext(payload);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().hostname()).isEqualTo("planeacion.gov.co");
        assertThat(documents.getFirst().snippets()).containsExactly("Texto normativo consultado.");
    }

    @Test
    void rejectsGovernmentUrlLookalikesAndCredentials() {
        assertThat(provider.officialHost("https://planning.example.gov.co/norma")).isEqualTo("planning.example.gov.co");
        assertThat(provider.officialHost("https://gov.co.example/norma")).isEmpty();
        assertThat(provider.officialHost("https://user@planeacion.gov.co/norma")).isEmpty();
        assertThat(provider.officialHost("http://planeacion.gov.co/norma")).isEmpty();
    }

    @Test
    void sendsOnlyTheMunicipalityAndGenericRegulationTermsToTheExternalSearch() {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        WebClient.Builder client = WebClient.builder().exchangeFunction(request -> {
            requestUri.set(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"grounding\":{\"generic\":[]}}")
                    .build());
        });
        var contextProvider = new BraveRegulatoryContextProvider(client, "test-key");

        contextProvider.search("Neiva").join();

        String query = URLDecoder.decode(requestUri.get().getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).startsWith("q=site:gov.co Neiva Colombia")
                .contains("construcción", "uso del suelo")
                .doesNotContain("projectDescription", "180 m2", "Descripción del proyecto")
                .doesNotContain("country=", "search_lang=");
    }
}
