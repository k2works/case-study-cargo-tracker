package com.example.billingms.config;

import com.sendgrid.SendGrid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知設定（IT8 T3.2 / ADR-0018）。
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
