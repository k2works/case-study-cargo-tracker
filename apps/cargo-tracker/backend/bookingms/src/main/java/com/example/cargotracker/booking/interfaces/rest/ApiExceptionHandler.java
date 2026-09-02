package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.interfaces.rest.ShipperController.DuplicateShipperEmailException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p>状態遷移違反は 409、業務規則違反は 422、投影に未反映は 202 と分ける。
 * どれも 500 にすると、利用者は「やり直せばよいのか」「入力が悪いのか」を判断できない。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateShipperEmailException.class)
    public ResponseEntity<Map<String, Object>> onDuplicateEmail(DuplicateShipperEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "SHIPPER_EMAIL_DUPLICATE", "message", e.getMessage()));
    }

    /** 値オブジェクトと集約が弾いた業務規則違反。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBusinessRuleViolation(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of("code", "BUSINESS_RULE_VIOLATION", "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalidRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("入力が正しくありません");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of("code", "INVALID_REQUEST", "message", message));
    }
}
