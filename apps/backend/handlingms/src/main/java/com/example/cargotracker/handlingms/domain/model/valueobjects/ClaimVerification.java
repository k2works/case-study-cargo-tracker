package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 引取確認を表す値オブジェクト（US16）。
 *
 * <p>CLAIM 種別の荷役作業時に必須。署名画像参照（{@code signatureRef}）または
 * 確認コード（{@code confirmationCode}）のいずれかが必須。</p>
 *
 * <p>関連: domain-model.md L817-822 / data-model.md L632-639</p>
 *
 * @param consigneeName     荷受人氏名（必須）
 * @param signatureRef      署名画像参照（任意、URI 等）
 * @param confirmationCode  確認コード（任意、例: AX9-2K7）
 * @param verifiedAt        確認日時（必須）
 */
public record ClaimVerification(
        String consigneeName,
        String signatureRef,
        String confirmationCode,
        LocalDateTime verifiedAt) {

    public ClaimVerification {
        Objects.requireNonNull(consigneeName, "consigneeName");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        if (consigneeName.isBlank()) {
            throw new IllegalArgumentException("consigneeName は空文字にできません");
        }
        boolean hasSignature = signatureRef != null && !signatureRef.isBlank();
        boolean hasCode = confirmationCode != null && !confirmationCode.isBlank();
        if (!hasSignature && !hasCode) {
            throw new IllegalArgumentException(
                    "ClaimVerification は signatureRef または confirmationCode のいずれかが必須です");
        }
    }
}
