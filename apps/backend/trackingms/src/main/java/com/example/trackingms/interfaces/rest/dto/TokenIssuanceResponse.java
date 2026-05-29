package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.model.JwtToken;

import java.time.LocalDateTime;

/**
 * 公開照会トークン発行レスポンス（US18 / ADR-0013）。
 *
 * @param token      発行された JWT 文字列。荷主・荷受人向けメールに URL クエリパラメータとして埋め込まれる
 * @param issuedAt   発行時刻
 * @param validUntil 有効期限（発行時刻 + 30 日、または配送完了 + 7 日のいずれか早い方）
 */
public record TokenIssuanceResponse(String token, LocalDateTime issuedAt, LocalDateTime validUntil) {

    public static TokenIssuanceResponse from(JwtToken token) {
        return new TokenIssuanceResponse(token.token(), token.issuedAt(), token.validUntil());
    }
}
