package com.jose.subscriptions.common;

import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio con código estable (para que el cliente pueda reaccionar a él)
 * y el estado HTTP que le corresponde.
 */
public abstract sealed class BusinessException extends RuntimeException
        permits BusinessException.NotFound, BusinessException.Conflict, BusinessException.RuleViolation {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public abstract HttpStatus status();

    /** 404: el recurso no existe. */
    public static final class NotFound extends BusinessException {
        public NotFound(String code, String message) {
            super(code, message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.NOT_FOUND;
        }
    }

    /** 409: la petición choca con el estado actual (p. ej. ya hay una suscripción activa). */
    public static final class Conflict extends BusinessException {
        public Conflict(String code, String message) {
            super(code, message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }
    }

    /** 422: la petición es válida sintácticamente pero incumple una regla de negocio. */
    public static final class RuleViolation extends BusinessException {
        public RuleViolation(String code, String message) {
            super(code, message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.UNPROCESSABLE_CONTENT;
        }
    }
}
