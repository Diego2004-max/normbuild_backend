package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class AiProviderFailureMessagesTest {

    @Test
    void identifiesInvalidHostedCredentialsWithoutExposingThem() {
        var error = WebClientResponseException.create(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null);

        assertThat(AiProviderFailureMessages.forCitizen(new AiProviderException("Failed", error)))
                .contains("GROQ_API_KEY").doesNotContain("Unauthorized");
    }

    @Test
    void identifiesRateLimits() {
        var error = WebClientResponseException.create(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit", null, null, null);

        assertThat(AiProviderFailureMessages.forCitizen(new AiProviderException("Failed", error)))
                .contains("límite de solicitudes");
    }
}
