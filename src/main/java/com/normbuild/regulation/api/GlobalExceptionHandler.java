package com.normbuild.regulation.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        return new ApiErrorResponse("La solicitud contiene datos inválidos.", details, OffsetDateTime.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpected(Exception exception) {
        return new ApiErrorResponse(
                "No fue posible procesar la solicitud en este momento.",
                List.of(resolveMessage(exception)),
                OffsetDateTime.now()
        );
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleAsyncTimeout() {
        return new ApiErrorResponse(
                "El análisis tardó más de lo esperado. Intenta nuevamente con una descripción más breve o revisa que Ollama esté respondiendo.",
                List.of("Tiempo máximo de procesamiento agotado."),
                OffsetDateTime.now()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return org.springframework.http.ResponseEntity.status(status).body(new ApiErrorResponse(
                exception.getReason() == null ? "No fue posible procesar la solicitud." : exception.getReason(),
                List.of(),
                OffsetDateTime.now()));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String resolveMessage(Exception exception) {
        Throwable resolved = exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = resolved.getMessage();
        if (message == null || message.isBlank()) {
            return resolved.getClass().getSimpleName();
        }
        return message;
    }
}
