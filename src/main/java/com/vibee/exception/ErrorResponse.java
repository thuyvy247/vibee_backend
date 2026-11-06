package com.vibee.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String code, String message, Map<String, String> errors) {

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(Instant.now(), status, code, message, null);
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> errors) {
        return new ErrorResponse(Instant.now(), status, code, message, errors);
    }
}
