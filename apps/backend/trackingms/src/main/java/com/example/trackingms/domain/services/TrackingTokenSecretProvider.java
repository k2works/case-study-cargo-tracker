package com.example.trackingms.domain.services;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * 公開追跡照会トークン用 secret 鍵プロバイダ（IT8 H2 持ち越し T1.6、ADR-0013 連動）。
 *
 * <p>四半期ローテーション運用時に「現行 secret + 直前の secret」の両方で検証できるよう、
 * 単一の {@code String secret} ではなく {@link SecretKey} の {@code activeSigningKey} +
 * {@code verifyingKeys}（複数）を返す。</p>
 *
 * <p>実装の選択肢:</p>
 *
 * <ul>
 *   <li>{@link StaticTrackingTokenSecretProvider}（IT8 T1.6、デフォルト）:
 *       application.yml / 環境変数から secret を取得。旧 secret も
 *       {@code tracking.public-token.previous-secret} で指定可能。</li>
 *   <li>AwsSecretsManagerTrackingTokenSecretProvider（IT9 持ち越し、ADR-0021 起票予定）:
 *       AWS Secrets Manager から AWSCURRENT / AWSPREVIOUS のバージョン段階を取得。
 *       Heroku Config Vars → AWS Secrets Manager + 四半期ローテーション + Lambda 自動回転へ移行。</li>
 * </ul>
 */
public interface TrackingTokenSecretProvider {

    /**
     * トークン発行時に署名する鍵（最新の secret）。
     */
    SecretKey activeSigningKey();

    /**
     * トークン検証時に試行する鍵一覧（現行 + 直前の旧 secret）。
     * 検証は順次試行し、最初に成功した鍵で通す（ローテーション期間の互換性確保）。
     */
    List<SecretKey> verifyingKeys();
}
