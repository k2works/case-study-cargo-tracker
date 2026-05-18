package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 追跡照会用 JWT トークン値オブジェクト（ADR-0013）。
 *
 * <p>JWT 文字列・発行時刻・実効有効期限（{@code min(exp, deliveredAt + grace)} を計算済み）
 * を保持する。実効有効期限はフロント側で「あと N 日」表示用に使用される。</p>
 */
public record JwtToken(String token, LocalDateTime issuedAt, LocalDateTime validUntil) {

    public JwtToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(validUntil, "validUntil");
        if (token.isBlank()) {
            throw new IllegalArgumentException("JWT token は空にできません");
        }
        if (validUntil.isBefore(issuedAt)) {
            throw new IllegalArgumentException("validUntil は issuedAt 以降である必要があります");
        }
    }
}
