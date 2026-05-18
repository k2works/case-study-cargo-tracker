package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * US18: 追跡照会用 JWT 発行レスポンス（管理者用 _internal API）。
 *
 * @param url        公開追跡 URL（フロントエンドにそのままコピーして配布される）
 * @param token      JWT 文字列
 * @param validUntil 実効有効期限
 */
public record IssueTrackingTokenResponse(
        String url,
        String token,
        LocalDateTime validUntil) {}
