package com.example.trackingms.domain.services;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AwsSecretsManagerTrackingTokenSecretProvider} の単体テスト（IT9 A2.2 / ADR-0021 / US27）。
 */
class AwsSecretsManagerTrackingTokenSecretProviderTest {

    private static final String SECRET_ID = "tracking/public-token";
    private static final String CURRENT_SECRET = "abcdefghijklmnopqrstuvwxyz123456"; // 32 bytes
    private static final String PREVIOUS_SECRET = "zyxwvutsrqponmlkjihgfedcba654321"; // 32 bytes

    private SecretsManagerClient client;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        client = mock(SecretsManagerClient.class);
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void 起動時に現行と直前の両シークレットを取得して検証鍵に登録する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            String value = "AWSCURRENT".equals(req.versionStage()) ? CURRENT_SECRET : PREVIOUS_SECRET;
            return GetSecretValueResponse.builder().secretString(value).build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();

        assertThat(provider.activeSigningKey()).isNotNull();
        assertThat(provider.verifyingKeys()).hasSize(2);
    }

    @Test
    void 直前シークレットが未存在の場合は現行のみで起動する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();

        assertThat(provider.activeSigningKey()).isNotNull();
        assertThat(provider.verifyingKeys()).hasSize(1);
    }

    @Test
    void 現行シークレットが取得不可なら起動失敗する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWSCURRENT");
    }

    @Test
    void 短すぎるシークレットは起動失敗する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString("too-short").build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWSCURRENT");
    }

    @Test
    void 再取得で新しい現行シークレットに切り替わる() {
        String newCurrent = "newSecret_aaaaaaaaaaaaaaaaaaaaaaa1"; // 32 bytes
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });
        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();
        var initialKey = provider.activeSigningKey();

        // 新しい AWSCURRENT を返すように変更
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
            }
            return GetSecretValueResponse.builder().secretString(newCurrent).build();
        });
        provider.refresh();

        assertThat(provider.activeSigningKey()).isNotSameAs(initialKey);
        assertThat(provider.verifyingKeys()).hasSize(2);
    }

    @Test
    void refresh_成功時はsuccessCounterがインクリメントされる() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();

        assertThat(meterRegistry.get("tracking.public_token.refresh.success").counter().count())
                .as("PostConstruct で refresh が 1 回成功")
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("tracking.public_token.refresh.consecutive_failures").gauge().value())
                .as("成功時は連続失敗 Gauge が 0 にリセット")
                .isEqualTo(0.0);
    }

    @Test
    void refresh_AWSCURRENT取得失敗時はfailureCounterと連続失敗Gaugeが増える() {
        // init() は成功させる（PostConstruct fail-fast 回避）
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();

        // 以降の refresh では AWSCURRENT 取得失敗を再現
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException.builder()
                        .message("simulated outage")
                        .build());

        provider.refresh();
        provider.refresh();
        provider.refresh();

        assertThat(meterRegistry.get("tracking.public_token.refresh.failure").counter().count())
                .as("失敗 3 回が Counter に記録される")
                .isEqualTo(3.0);
        assertThat(meterRegistry.get("tracking.public_token.refresh.consecutive_failures").gauge().value())
                .as("連続失敗 3 回が Gauge に反映される（アラート閾値 3 で発火）")
                .isEqualTo(3.0);
    }

    @Test
    void refresh_失敗後に成功すると連続失敗Gaugeが0にリセットされる() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });
        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID, meterRegistry);
        provider.init();

        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException.builder()
                        .message("simulated outage")
                        .build());
        provider.refresh();
        provider.refresh();

        assertThat(meterRegistry.get("tracking.public_token.refresh.consecutive_failures").gauge().value())
                .isEqualTo(2.0);

        // 復旧後の成功
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            if ("AWSPREVIOUS".equals(req.versionStage())) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return GetSecretValueResponse.builder().secretString(CURRENT_SECRET).build();
        });
        provider.refresh();

        assertThat(meterRegistry.get("tracking.public_token.refresh.consecutive_failures").gauge().value())
                .as("成功 1 回で連続失敗 Gauge が 0 にリセット")
                .isEqualTo(0.0);
    }
}
