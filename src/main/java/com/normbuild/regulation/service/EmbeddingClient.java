package com.normbuild.regulation.service;

import com.normbuild.config.NormBuildAiProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;

@Component
public class EmbeddingClient {

    private final WebClient webClient;
    private final NormBuildAiProperties properties;

    public EmbeddingClient(WebClient.Builder webClientBuilder, NormBuildAiProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    public List<Double> createEmbedding(String text) {
        Map<String, String> request = Map.of(
                "model", properties.embeddingModelName(),
                "prompt", text
        );
        Map<String, Object> response = webClient.post()
                .uri(properties.embeddingEndpoint())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(properties.embeddingTimeoutSeconds()))
                .onErrorMap(throwable -> new AiProviderException("No fue posible generar el vector semántico con Ollama.", throwable))
                .block();
        Object embedding = response == null ? null : response.get("embedding");
        if (!(embedding instanceof List<?> values)) {
            throw new AiProviderException("Ollama no devolvió un vector semántico válido.");
        }
        try {
            return values.stream()
                    .map(Number.class::cast)
                    .map(Number::doubleValue)
                    .toList();
        } catch (RuntimeException exception) {
            throw Exceptions.propagate(new AiProviderException("El vector semántico contiene valores inválidos.", exception));
        }
    }
}
