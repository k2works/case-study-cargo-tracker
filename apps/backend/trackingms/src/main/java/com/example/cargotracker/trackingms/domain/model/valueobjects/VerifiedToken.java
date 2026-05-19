package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JWT 検証結果値オブジェクト（TI08-3）。
 *
 * <p>検証成功後の追跡番号と実効有効期限（{@code min(exp, deliveredAt + grace)}）を保持する。
 * {@link com.example.cargotracker.trackingms.domain.model.services.TrackingTokenService#verify}
 * の戻り値として使用し、コントローラーの dummyValidUntil を解消する。</p>
 */
public record VerifiedToken(TrackingNumber trackingNumber, LocalDateTime expiresAt) {

    public VerifiedToken {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
