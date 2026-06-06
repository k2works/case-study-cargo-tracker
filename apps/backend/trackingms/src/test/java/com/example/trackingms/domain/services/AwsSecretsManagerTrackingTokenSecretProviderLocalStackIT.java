package com.example.trackingms.domain.services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretVersionStageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AwsSecretsManagerTrackingTokenSecretProvider} の LocalStack 統合テスト
 * （IT9 A2.4 / ADR-0021 / US27）。
 *
 * <p>本テストは AWS SDK + 実 LocalStack コンテナ（Testcontainers）で結合検証する。
 * Mockito ベースの単体テスト（{@code AwsSecretsManagerTrackingTokenSecretProviderTest}）と
 * 異なり、実 HTTP / 認証経路の動作を保証する。</p>
 *
 * <p>検証シナリオ:</p>
 * <ol>
 *   <li>AWSCURRENT のみで起動 → activeSigningKey + verifyingKeys(1)</li>
 *   <li>AWSPENDING 追加 → AWSCURRENT へ昇格 → refresh で新 active key + AWSPREVIOUS 検証鍵</li>
 * </ol>
 *
 * <p>前提: Docker daemon が起動していること。CI では Docker 環境を用意する。</p>
 */
@Tag("localstack-integration")
class AwsSecretsManagerTrackingTokenSecretProviderLocalStackIT {

    private static final String SECRET_ID = "tracking/public-token-test";
    private static final String CURRENT_SECRET = "currentSecretValueWith32BytesPlusXYZ"; // 36 bytes
    private static final String NEW_SECRET = "newRotatedSecretValueWith32BytesPlus"; // 36 bytes

    private static LocalStackContainer localStack;
    private static SecretsManagerClient client;

    @BeforeAll
    static void setupContainer() {
        localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
                .withServices(Service.SECRETSMANAGER);
        localStack.start();
        client = SecretsManagerClient.builder()
                .endpointOverride(localStack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .region(Region.of(localStack.getRegion()))
                .build();
    }

    @AfterAll
    static void teardownContainer() {
        if (client != null) {
            client.close();
        }
        if (localStack != null) {
            localStack.stop();
        }
    }

    @Test
    void 起動時に_AWSCURRENT_のみで初期化される() {
        String secretId = SECRET_ID + "-init-" + UUID.randomUUID().toString().substring(0, 8);
        client.createSecret(CreateSecretRequest.builder()
                .name(secretId)
                .secretString(CURRENT_SECRET)
                .build());

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, secretId);
        provider.init();

        assertThat(provider.activeSigningKey()).isNotNull();
        assertThat(provider.verifyingKeys()).hasSize(1);
    }

    @Test
    void ローテーション後に_refresh_で新しい現行鍵と直前鍵が取得される() {
        String secretId = SECRET_ID + "-rotate-" + UUID.randomUUID().toString().substring(0, 8);
        // 初期 AWSCURRENT
        var initialCreate = client.createSecret(CreateSecretRequest.builder()
                .name(secretId)
                .secretString(CURRENT_SECRET)
                .build());
        String initialVersionId = initialCreate.versionId();

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, secretId);
        provider.init();
        var initialActiveKey = provider.activeSigningKey();
        assertThat(provider.verifyingKeys()).hasSize(1);

        // AWSPENDING に新 secret を追加して AWSCURRENT に昇格（Lambda rotation 相当）
        String newVersionToken = UUID.randomUUID().toString();
        client.putSecretValue(PutSecretValueRequest.builder()
                .secretId(secretId)
                .clientRequestToken(newVersionToken)
                .secretString(NEW_SECRET)
                .versionStages("AWSPENDING")
                .build());
        client.updateSecretVersionStage(UpdateSecretVersionStageRequest.builder()
                .secretId(secretId)
                .versionStage("AWSCURRENT")
                .moveToVersionId(newVersionToken)
                .removeFromVersionId(initialVersionId)
                .build());

        // refresh で新 AWSCURRENT を取得 + 旧版は AWSPREVIOUS として検証鍵に残る
        provider.refresh();

        assertThat(provider.activeSigningKey()).isNotSameAs(initialActiveKey);
        assertThat(provider.verifyingKeys()).hasSize(2);
    }
}
