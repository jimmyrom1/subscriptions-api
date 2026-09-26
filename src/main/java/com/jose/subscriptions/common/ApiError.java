package com.jose.subscriptions.common;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Formato único para todos los errores de la API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, Instant timestamp, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), Map.of());
    }
}
