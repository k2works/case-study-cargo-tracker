package com.example.handlingms.domain.model;

import java.time.LocalDateTime;

/**
 * 引取時の荷受人確認情報（US16 / domain-model.md / data-model.md claim_verification）。
 *
 * <p>引取（{@code HandlingType.CLAIM}）では荷受人確認が必須。確認手段は署名参照
 * （{@code signatureRef}）または確認コード（{@code confirmationCode}）のいずれか少なくとも 1 つ。
 * 両方 null の場合は {@code IllegalArgumentException}。</p>
 *
 * @param consigneeName    荷受人氏名（必須）
 * @param signatureRef     署名参照（任意。署名画像 / PDF などの URL/ID）
 * @param confirmationCode 確認コード（任意。事前共有された短い英数字）
 * @param verifiedAt       確認日時
 */
public record ClaimVerification(
        String consigneeName,
        String signatureRef,
        String confirmationCode,
        LocalDateTime verifiedAt
) {
    public ClaimVerification {
        if (consigneeName == null || consigneeName.isBlank()) {
            throw new IllegalArgumentException("荷受人氏名は必須です");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("確認日時は必須です");
        }
        if ((signatureRef == null || signatureRef.isBlank())
                && (confirmationCode == null || confirmationCode.isBlank())) {
            throw new IllegalArgumentException(
                    "荷受人確認には署名参照または確認コードのいずれかが必要です");
        }
    }
}
