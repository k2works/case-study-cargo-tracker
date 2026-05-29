package com.example.trackingms.domain.model;

import java.time.LocalDateTime;

/**
 * 公開追跡照会用の時限署名トークン（ADR-0013 / US18）。
 *
 * <p>HS256 で署名された JWT 文字列と、発行時刻・有効期限を保持する。
 * トークンは荷主・荷受人向けメールに URL クエリパラメータ（{@code ?token=<JWT>}）
 * として埋め込まれ、公開エンドポイント {@code GET /api/v1/public/tracking/{tn}}
 * で {@code PublicTrackingTokenFilter} により検証される。</p>
 */
public record JwtToken(String token, LocalDateTime issuedAt, LocalDateTime validUntil) {

    public JwtToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token は null または空にできません");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issuedAt は null にできません");
        }
        if (validUntil == null) {
            throw new IllegalArgumentException("validUntil は null にできません");
        }
        if (!validUntil.isAfter(issuedAt)) {
            throw new IllegalArgumentException("validUntil は issuedAt より後である必要があります");
        }
    }
}
