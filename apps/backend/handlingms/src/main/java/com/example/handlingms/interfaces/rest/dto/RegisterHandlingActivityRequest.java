package com.example.handlingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * 荷役作業登録リクエスト（US15・US16 / IT5 3.x）。
 *
 * <p>{@code handlingType} は HandlingType の列挙名（RECEIVE / LOAD / UNLOAD / CLAIM / CUSTOMS）。
 * LOAD / UNLOAD では {@code voyageNumber} 必須、CLAIM では {@code claimVerification} 必須。</p>
 */
public record RegisterHandlingActivityRequest(
        String activityId,
        String trackingNumber,
        String handlingType,
        LocalDateTime occurredAt,
        String unlocode,
        String voyageNumber,
        String handlerId,
        ClaimVerificationRequest claimVerification
) {
    public record ClaimVerificationRequest(
            String consigneeName,
            String signatureRef,
            String confirmationCode,
            LocalDateTime verifiedAt
    ) {
    }
}
