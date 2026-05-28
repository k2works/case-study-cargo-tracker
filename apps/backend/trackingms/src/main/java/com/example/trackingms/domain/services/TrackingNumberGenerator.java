package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.TrackingNumber;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 追跡番号採番ドメインサービス（US14）。
 *
 * <p>{@code TRK-} + 大文字英数 10 桁の {@link TrackingNumber} を {@link SecureRandom} で
 * 推測困難に採番する。36 文字種 × 10 桁で約 3.6 兆通りあり、IT5 規模では実用上衝突しない。
 * 永続的な一意性は data-model.md の {@code tracking_summary.tracking_number UNIQUE} 制約で
 * 保証する（DB レベルで二重採番を検出）。</p>
 *
 * <p>採番は副作用（乱数生成）を持つため、集約内ではなく本ドメインサービスに分離する。
 * Saga / アプリケーション層が本サービスを呼び出して {@code InitializeTrackingCommand}
 * の引数に渡す。</p>
 */
@Component
public class TrackingNumberGenerator {

    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int RANDOM_LENGTH = 10;
    private static final String PREFIX = "TRK-";

    private final SecureRandom random = new SecureRandom();

    public TrackingNumber generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + RANDOM_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]);
        }
        return TrackingNumber.of(sb.toString());
    }
}
