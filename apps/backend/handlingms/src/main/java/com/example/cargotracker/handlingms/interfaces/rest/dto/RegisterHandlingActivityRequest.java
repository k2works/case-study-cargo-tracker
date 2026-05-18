package com.example.cargotracker.handlingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 荷役作業登録リクエスト DTO（POST /api/v1/handling/activities）。
 */
public record RegisterHandlingActivityRequest(
        @NotBlank String trackingNumber,
        @NotBlank String handlingType,
        @NotBlank String unlocode,
        @NotNull LocalDateTime occurredAt,
        String voyageNumber,
        @NotBlank String operatorId,
        ClaimVerificationDto claimVerification) {

    /** CLAIM 種別時の荷受人確認（US16）。 */
    public record ClaimVerificationDto(
            @NotBlank String consigneeName,
            String signatureRef,
            String confirmationCode) {
    }
}
