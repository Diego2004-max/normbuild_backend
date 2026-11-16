package com.normbuild.regulation.service;

import com.normbuild.config.NormBuildAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeoutException;

@Component
public class LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmClient.class);
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)");

    private final WebClient webClient;
    private final NormBuildAiProperties properties;
    @Value("${NORMBUILD_OLLAMA_TOKEN:}")
    private String ollamaToken = "";
    @Value("${GROQ_API_KEY:}")
    private String groqApiKey = "";
    @Value("${NORMBUILD_HOSTED_MODEL:llama-3.1-8b-instant}")
    private String hostedModel = "llama-3.1-8b-instant";

    public LlmClient(WebClient.Builder webClientBuilder, NormBuildAiProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    public String generateChecklist(String prompt) {
        if (!groqApiKey.isBlank()) {
            return generateHostedChecklist(prompt);
        }
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
                .headers(headers -> { if (!ollamaToken.isBlank()) headers.setBearerAuth(ollamaToken); })
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
        if (!Boolean.TRUE.equals(response.get("done"))) {
            throw new AiProviderException("El modelo local no terminó el checklist dentro del límite de generación.");
        }
        String completedText = text.trim();
        if ("length".equals(response.get("done_reason"))) {
            Matcher sentenceEnds = SENTENCE_END.matcher(completedText);
            int lastCompleteSentence = -1;
            while (sentenceEnds.find()) {
                lastCompleteSentence = sentenceEnds.end();
            }
            if (lastCompleteSentence < 20) {
                throw new AiProviderException("El modelo local no produjo suficiente texto para una respuesta completa.");
            }
            completedText = completedText.substring(0, lastCompleteSentence).stripTrailing()
                    + " La respuesta est\u00e1 resumida por la capacidad del modelo local. "
                    + "Confirma los l\u00edmites en la ficha normativa del predio. Riesgo: Por verificar.";
            LOGGER.warn("LLM output reached the configured token limit; returning only complete sentences");
        }
        LOGGER.info("LLM generation completed: model={}, loadSeconds={}, generationSeconds={}, outputTokens={}",
                properties.modelName(), seconds(response.get("load_duration")),
                seconds(response.get("total_duration")), response.get("eval_count"));
        return completedText;
    }

    private String generateHostedChecklist(String prompt) {
        JsonNode response = webClient.post()
                .uri("https://api.groq.com/openai/v1/chat/completions")
                .headers(headers -> headers.setBearerAuth(groqApiKey))
                .bodyValue(Map.of(
                        "model", hostedModel,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.1,
                        "max_completion_tokens", Math.max(512, properties.llmMaxOutputTokens())))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(Math.min(90, properties.llmTimeoutSeconds())))
                .onErrorMap(error -> new AiProviderException("El proveedor de IA no pudo completar la respuesta.", error))
                .block();
        String answer = response == null ? "" : response.path("choices").path(0)
                .path("message").path("content").asText("").strip();
        if (answer.isBlank()) {
            throw new AiProviderException("El proveedor de IA devolvió una respuesta vacía.");
        }
        if ("length".equals(response.path("choices").path(0).path("finish_reason").asText())) {
            throw new AiProviderException("El proveedor de IA alcanzó el límite de generación antes de completar la respuesta.");
        }
        LOGGER.info("Hosted LLM generation completed: model={}", hostedModel);
        return answer;
    }

    private double seconds(Object duration) {
        return duration instanceof Number value ? value.doubleValue() / 1_000_000_000 : 0;
    }
}
