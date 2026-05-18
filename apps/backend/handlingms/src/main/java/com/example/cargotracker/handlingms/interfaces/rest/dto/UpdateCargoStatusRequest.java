package com.example.cargotracker.handlingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 貨物状態手動更新リクエスト DTO（US17）。
 */
public record UpdateCargoStatusRequest(
        @NotBlank String newStatus,
        @NotBlank String unlocode,
        @NotNull LocalDateTime updatedAt,
        @NotBlank String operatorId) {
}
