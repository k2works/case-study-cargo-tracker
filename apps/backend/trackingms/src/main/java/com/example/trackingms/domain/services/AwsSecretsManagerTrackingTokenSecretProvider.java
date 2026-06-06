package com.example.trackingms.domain.services;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Secrets Manager 経由で公開追跡トークンの secret を取得する
 * {@link TrackingTokenSecretProvider} 実装（IT9 A2 / ADR-0021 / US27）。
 *
 * <p>{@code tracking.public-token.provider=aws-secrets-manager} のときに有効化される。
 * AWSCURRENT バージョン段階を {@code activeSigningKey}、AWSPREVIOUS（任意）を
 * {@code verifyingKeys} に追加することで、Lambda による自動回転中も発行・検証の連続性を保つ。</p>
 *
 * <p>{@link #refresh()} は {@code @Scheduled} で定期実行され、Lambda rotation Function による
 * secret 更新後に新値を反映する（デフォルト 5 分間隔、{@code tracking.public-token.refresh-rate-ms}）。</p>
 *
 * <p>運用前提（ADR-0021）:</p>
 *
 * <ul>
 *   <li>AWS Secrets Manager に {@code tracking/public-token} 等のシークレット名を作成</li>
 *   <li>Lambda rotation Function を四半期 cron で実行（新 secret を生成 → AWSCURRENT 段階更新）</li>
 *   <li>古い secret は AWSPREVIOUS 段階に自動降格、本クラスが検証時に併用</li>
 *   <li>IAM Role で {@code secretsmanager:GetSecretValue} 権限を付与</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "tracking.public-token.provider", havingValue = "aws-secrets-manager")
public class AwsSecretsManagerTrackingTokenSecretProvider implements TrackingTokenSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(
            AwsSecretsManagerTrackingTokenSecretProvider.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String STAGE_CURRENT = "AWSCURRENT";
    private static final String STAGE_PREVIOUS = "AWSPREVIOUS";

    private final SecretsManagerClient client;
    private final String secretId;

    private volatile SecretKey activeKey;
    private volatile List<SecretKey> verifyingKeys = List.of();

    public AwsSecretsManagerTrackingTokenSecretProvider(
            SecretsManagerClient client,
            @Value("${tracking.public-token.aws.secret-id}") String secretId
    ) {
        this.client = client;
        this.secretId = secretId;
    }

    /**
     * 起動時に AWSCURRENT / AWSPREVIOUS を初回取得する。Secrets Manager 接続失敗時は
     * 起動失敗にする（fail-fast）。
     */
    @PostConstruct
    public void init() {
        refresh();
        if (activeKey == null) {
            throw new IllegalStateException(
                    "AWS Secrets Manager から AWSCURRENT secret を取得できませんでした: " + secretId);
        }
    }

    /**
     * 定期 refresh。Lambda rotation Function による secret 更新後に新値を反映する。
     * デフォルト 5 分（300000 ms）、application.yml の
     * {@code tracking.public-token.refresh-rate-ms} で調整可能。
     */
    @Scheduled(fixedRateString = "${tracking.public-token.refresh-rate-ms:300000}")
    public void refresh() {
        SecretKey current = fetchKey(STAGE_CURRENT);
        SecretKey previous = fetchKeyOptional(STAGE_PREVIOUS);
        if (current == null) {
            log.warn("AWS Secrets Manager AWSCURRENT 取得失敗（前回値を維持）: secret_id={}", secretId);
            return;
        }
        List<SecretKey> newVerifying = new ArrayList<>();
        newVerifying.add(current);
        if (previous != null) {
            newVerifying.add(previous);
        }
        this.activeKey = current;
        this.verifyingKeys = List.copyOf(newVerifying);
        log.info("AWS Secrets Manager refresh 完了: secret_id={}, verifying_keys={}件",
                secretId, newVerifying.size());
    }

    @Override
    public SecretKey activeSigningKey() {
        return activeKey;
    }

    @Override
    public List<SecretKey> verifyingKeys() {
        return verifyingKeys;
    }

    private SecretKey fetchKey(String versionStage) {
        try {
            return fetchKeyOrThrow(versionStage);
        } catch (Exception e) {
            log.warn("AWS Secrets Manager 取得失敗: secret_id={}, stage={}, reason={}",
                    secretId, versionStage, e.getMessage());
            return null;
        }
    }

    private SecretKey fetchKeyOptional(String versionStage) {
        try {
            return fetchKeyOrThrow(versionStage);
        } catch (ResourceNotFoundException e) {
            // AWSPREVIOUS は初回回転前は存在しないので OK
            return null;
        } catch (Exception e) {
            log.debug("AWS Secrets Manager オプショナル取得失敗: secret_id={}, stage={}, reason={}",
                    secretId, versionStage, e.getMessage());
            return null;
        }
    }

    private SecretKey fetchKeyOrThrow(String versionStage) {
        GetSecretValueResponse response = client.getSecretValue(
                GetSecretValueRequest.builder()
                        .secretId(secretId)
                        .versionStage(versionStage)
                        .build()
        );
        String value = response.secretString();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "secret " + secretId + " の " + versionStage + " 段階が空です");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "secret " + secretId + " の " + versionStage
                            + " 段階が " + MIN_SECRET_BYTES + " バイト未満です（現在: "
                            + bytes.length + " バイト）");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
