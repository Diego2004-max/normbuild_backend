package com.normbuild.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "normbuild.ai")
public record NormBuildAiProperties(
        @Min(1) int embeddingDimensions,
        @NotBlank String llmEndpoint,
        @NotBlank String embeddingEndpoint,
        @NotBlank String modelName,
        @NotBlank String embeddingModelName,
        @DecimalMin("0.0") double similarityThreshold,
        @Min(1) int maxContextResults,
        @Min(1) int embeddingTimeoutSeconds,
        @Min(1) int llmTimeoutSeconds,
        @Min(256) int llmContextTokens,
        @Min(1) int llmMaxOutputTokens,
        @NotBlank String modelKeepAlive
) {
}
