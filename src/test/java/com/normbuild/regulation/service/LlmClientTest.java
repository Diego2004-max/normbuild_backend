package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.normbuild.config.NormBuildAiProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

class LlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
    private HttpServer server;
    private String responseBody;
    private int responseStatus;
    private LlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseBody = """
                {"response":"Revisa la norma del predio.","done":true,"done_reason":"stop","eval_count":8}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/generate";
        NormBuildAiProperties properties = new NormBuildAiProperties(
                768, endpoint, endpoint, "llama3.1", "nomic-embed-text",
                0.35, 5, 30, 120, 4096, 512, "10m");
        client = new LlmClient(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsConfiguredResourceLimitsAndAcceptsCompleteGeneration() {
        assertThat(client.generateChecklist("Test prompt")).isEqualTo("Revisa la norma del predio.");

        JsonNode request = capturedRequest.get();
        assertThat(request.path("model").asText()).isEqualTo("llama3.1");
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.path("keep_alive").asText()).isEqualTo("10m");
        assertThat(request.path("options").path("num_ctx").asInt()).isEqualTo(4096);
        assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(512);
        assertThat(request.path("options").path("temperature").asDouble()).isEqualTo(0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"response\":\"Respuesta parcial.\",\"done\":true,\"done_reason\":\"length\"}",
            "{\"response\":\"Respuesta parcial.\",\"done\":false}",
            "{\"response\":\"\",\"done\":true}"
    })
    void rejectsIncompleteOrEmptyGeneration(String body) {
        responseBody = body;

        assertThatThrownBy(() -> client.generateChecklist("Test prompt")).isInstanceOf(AiProviderException.class);
    }

    @Test
    void reportsProviderFailureWithoutCallingItATimeout() {
        responseStatus = 404;
        responseBody = "{\"error\":\"model unavailable\"}";

        assertThatThrownBy(() -> client.generateChecklist("Test prompt"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Comprueba que Ollama esté disponible");
    }
}
