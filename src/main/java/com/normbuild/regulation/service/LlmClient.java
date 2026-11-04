package com.normbuild.regulation.service;

import com.normbuild.config.NormBuildAiProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeoutException;

@Component
public class LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmClient.class);

    private final WebClient webClient;
    private final NormBuildAiProperties properties;

    public LlmClient(WebClient.Builder webClientBuilder, NormBuildAiProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    public String generateChecklist(String prompt) {
        Map<String, Object> request = Map.of(
                "model", properties.modelName(),
                "prompt", prompt,
                "stream", false,
                "keep_alive", properties.modelKeepAlive(),
                "options", Map.of(
                        "num_ctx", properties.llmContextTokens(),
                        "num_predict", properties.llmMaxOutputTokens(),
                        "temperature", 0.1
                )
        );
        Map<String, Object> response = webClient.post()
                .uri(properties.llmEndpoint())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(properties.llmTimeoutSeconds()))
                .onErrorMap(throwable -> new AiProviderException(
                        throwable instanceof TimeoutException
                                ? "El modelo local agotó el tiempo disponible para generar la respuesta."
                                : "No fue posible obtener una respuesta del modelo local. Comprueba que Ollama esté disponible.",
                        throwable))
                .block();
        Object answer = response == null ? null : response.get("response");
        if (!(answer instanceof String text) || text.isBlank()) {
            throw new AiProviderException("El modelo local devolvió una respuesta vacía.");
        }
        if (!Boolean.TRUE.equals(response.get("done")) || "length".equals(response.get("done_reason"))) {
            throw new AiProviderException("El modelo local no terminó el checklist dentro del límite de generación.");
        }
        LOGGER.info("LLM generation completed: model={}, loadSeconds={}, generationSeconds={}, outputTokens={}",
                properties.modelName(), seconds(response.get("load_duration")),
                seconds(response.get("total_duration")), response.get("eval_count"));
        return text.trim();
    }

    private double seconds(Object duration) {
        return duration instanceof Number value ? value.doubleValue() / 1_000_000_000 : 0;
    }
}
