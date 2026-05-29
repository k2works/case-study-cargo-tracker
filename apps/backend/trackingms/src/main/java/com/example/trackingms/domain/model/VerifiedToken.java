package com.example.trackingms.domain.model;

import java.time.LocalDateTime;

/**
 * 公開追跡照会トークンの検証結果（ADR-0013 / US18）。
 *
 * <p>{@code TrackingTokenService.verify()} が JWT を検証成功した場合に返す値オブジェクト。
 * 検証失敗時は例外を投げるため、本クラスは「検証成功」の事実そのものを表す。</p>
 */
public record VerifiedToken(
        TrackingNumber trackingNumber,
        String subjectId,
        TokenRole role,
        LocalDateTime expiresAt
) {

    public VerifiedToken {
        if (trackingNumber == null) {
            throw new IllegalArgumentException("trackingNumber は null にできません");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId は null または空にできません");
        }
        if (role == null) {
            throw new IllegalArgumentException("role は null にできません");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt は null にできません");
        }
    }
}
