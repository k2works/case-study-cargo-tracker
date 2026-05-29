package com.example.trackingms.domain.services;

/**
 * 公開追跡照会トークンの検証失敗を表す例外（ADR-0013 / US18）。
 *
 * <p>署名不正・期限切れ・追跡番号不一致・audience 不一致など、検証経路で発生する
 * 全ての異常を本例外に集約する。Spring Security の {@code PublicTrackingTokenFilter}
 * では本例外を捕捉して HTTP 403 Forbidden を返す（ui_design.md L738 準拠）。</p>
 */
public class TrackingTokenInvalidException extends RuntimeException {

    public TrackingTokenInvalidException(String message) {
        super(message);
    }

    public TrackingTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
