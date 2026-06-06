package com.example.trackingms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS Secrets Manager Client の Spring 設定（IT9 A2 / ADR-0021 / US27）。
 *
 * <p>{@code tracking.public-token.provider=aws-secrets-manager} のときに有効化され、
 * region は {@code tracking.public-token.aws.region}（デフォルト ap-northeast-1）から取得する。
 * クレデンシャルは AWS SDK デフォルトチェイン（環境変数 / IAM Role / ECS Task Role 等）。</p>
 *
 * <p>LocalStack 統合テスト時は {@code @TestConfiguration} で endpoint override + 静的キーで
 * 差し替える（A2.4）。本クラスは本番経路のみ提供。</p>
 */
@Configuration
@ConditionalOnProperty(name = "tracking.public-token.provider", havingValue = "aws-secrets-manager")
public class AwsSecretsManagerConfig {

    @Bean
    @ConditionalOnMissingBean
    public SecretsManagerClient secretsManagerClient(
            @Value("${tracking.public-token.aws.region:ap-northeast-1}") String regionId
    ) {
        return SecretsManagerClient.builder()
                .region(Region.of(regionId))
                .build();
    }
}
