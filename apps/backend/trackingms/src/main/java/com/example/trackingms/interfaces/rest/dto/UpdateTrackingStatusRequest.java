package com.example.trackingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 貨物状態手動更新リクエスト DTO
 */
public record UpdateTrackingStatusRequest(
        @NotBlank String newStatus
) {}
