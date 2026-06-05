package com.example.trackingms.domain.services;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 静的設定（application.yml / 環境変数）から secret を取得する
 * {@link TrackingTokenSecretProvider} 実装（IT8 T1.6、デフォルト）。
 *
 * <p>{@code tracking.public-token.secret}（必須、現行 secret、32 バイト以上）と
 * {@code tracking.public-token.previous-secret}（任意、ローテーション期間中の旧 secret、
 * 32 バイト以上）を読み込む。発行は現行のみ、検証は両方を順次試行する。</p>
 *
 * <p>四半期ローテーションのフロー:</p>
 *
 * <ol>
 *   <li>新 secret を生成し {@code tracking.public-token.previous-secret} に
 *       旧 secret（現行）を、{@code tracking.public-token.secret} に新 secret を設定して再デプロイ</li>
 *   <li>1 四半期（最大トークン有効期間 30 日 + マージン）経過後、旧 secret 設定を削除して再デプロイ</li>
 * </ol>
 *
 * <p>AWS Secrets Manager 経由の自動ローテーションは IT9 持ち越し
 * （AWS SDK 統合 + AWSCURRENT/AWSPREVIOUS 取得 + Lambda 自動回転、ADR-0021 起票予定）。</p>
 */
@Component
public class StaticTrackingTokenSecretProvider implements TrackingTokenSecretProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey activeKey;
    private final List<SecretKey> verifyingKeys;

    public StaticTrackingTokenSecretProvider(
            @Value("${tracking.public-token.secret}") String secret,
            @Value("${tracking.public-token.previous-secret:}") String previousSecret) {
        this.activeKey = toKey("tracking.public-token.secret", secret);
        List<SecretKey> keys = new ArrayList<>();
        keys.add(activeKey);
        if (previousSecret != null && !previousSecret.isBlank()) {
            keys.add(toKey("tracking.public-token.previous-secret", previousSecret));
        }
        this.verifyingKeys = List.copyOf(keys);
    }

    private SecretKey toKey(String name, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    name + " は " + MIN_SECRET_BYTES + " バイト以上である必要があります（現在: "
                            + bytes.length + " バイト）");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public SecretKey activeSigningKey() {
        return activeKey;
    }

    @Override
    public List<SecretKey> verifyingKeys() {
        return verifyingKeys;
    }
}
