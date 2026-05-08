package com.example.trackingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 追跡番号発行リクエスト DTO
 */
public record IssueTrackingNumberRequest(
        @NotBlank String bookingId
) {}
