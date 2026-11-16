package com.normbuild.regulation.service;

import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class AiProviderFailureMessages {

    private AiProviderFailureMessages() {
    }

    public static String forCitizen(AiProviderException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof WebClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 401, 403 -> "El proveedor de IA rechazó la clave configurada. Revisa GROQ_API_KEY en Render.";
                case 429 -> "El proveedor de IA alcanzó su límite de solicitudes. Espera unos minutos e inténtalo nuevamente.";
                case 400, 404 -> "El proveedor de IA rechazó el modelo o los parámetros configurados. Revisa NORMBUILD_HOSTED_MODEL en Render.";
                default -> "El proveedor de IA no pudo completar la respuesta. Inténtalo nuevamente.";
            };
        }
        return "No se pudo conectar con el proveedor de IA. Verifica su disponibilidad e inténtalo nuevamente.";
    }
}
