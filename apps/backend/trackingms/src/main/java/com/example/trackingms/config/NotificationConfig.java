package com.example.trackingms.config;

import com.sendgrid.SendGrid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知設定（IT8 T3.1 / ADR-0018）。
 *
 * <p>{@link NotificationProperties} を有効化し、{@code notification.adapter=sendgrid} 時のみ
 * {@link SendGrid} Bean を生成する。</p>
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    @ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")
    public SendGrid sendGrid(NotificationProperties properties) {
        return new SendGrid(properties.sendgrid().apiKey());
    }
}
