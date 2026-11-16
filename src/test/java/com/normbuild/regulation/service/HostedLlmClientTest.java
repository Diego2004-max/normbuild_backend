package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.normbuild.config.NormBuildAiProperties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class HostedLlmClientTest {

    @Test
    void sendsAuthenticatedChatRequestAndReadsCompleteAnswer() throws Exception {
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        var exchange = (org.springframework.web.reactive.function.client.ExchangeFunction) request -> {
            headers.set(request.headers());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"choices\":[{\"message\":{\"content\":\"Consulta la ficha normativa.\"},\"finish_reason\":\"stop\"}]}")
                    .build());
        };
        var client = new LlmClient(WebClient.builder().exchangeFunction(exchange), properties());
        ReflectionTestUtils.setField(client, "groqApiKey", "test-key");
        assertThat(ReflectionTestUtils.getField(client, "hostedModel")).isEqualTo("openai/gpt-oss-20b");

        assertThat(client.generateChecklist("Contexto oficial")).isEqualTo("Consulta la ficha normativa.");
        assertThat(headers.get().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
    }

    private NormBuildAiProperties properties() {
        return new NormBuildAiProperties(768, "http://localhost:11434/api/generate",
                "http://localhost:11434/api/embeddings", "llama3.1", "nomic-embed-text",
                0.35, 3, 30, 120, 4096, 256, "10m");
    }
}
