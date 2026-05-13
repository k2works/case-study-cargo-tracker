package com.example.cargotracker.authms.interfaces.rest.dto;

import java.util.List;

public record ValidationErrorResponse(
        int status,
        String message,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {}
}
