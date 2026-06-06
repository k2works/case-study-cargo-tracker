package com.example.trackingms.domain.services;

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

    @BeforeEach
    void setup() {
        client = mock(SecretsManagerClient.class);
    }

    @Test
    void 起動時に現行と直前の両シークレットを取得して検証鍵に登録する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenAnswer(invocation -> {
            GetSecretValueRequest req = invocation.getArgument(0);
            String value = "AWSCURRENT".equals(req.versionStage()) ? CURRENT_SECRET : PREVIOUS_SECRET;
            return GetSecretValueResponse.builder().secretString(value).build();
        });

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID);
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
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID);
        provider.init();

        assertThat(provider.activeSigningKey()).isNotNull();
        assertThat(provider.verifyingKeys()).hasSize(1);
    }

    @Test
    void 現行シークレットが取得不可なら起動失敗する() {
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        AwsSecretsManagerTrackingTokenSecretProvider provider =
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID);

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
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID);

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
                new AwsSecretsManagerTrackingTokenSecretProvider(client, SECRET_ID);
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
}
