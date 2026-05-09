package com.example.authms.interfaces.rest.dto;

import java.util.List;

/**
 * バリデーションエラーレスポンス DTO
 */
public record ValidationErrorResponse(
        int status,
        String message,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {}
}
