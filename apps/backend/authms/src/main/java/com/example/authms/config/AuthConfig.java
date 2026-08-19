package com.example.authms.config;

import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.PasswordVerifier;
import com.example.authms.application.port.TokenIssuer;
import com.example.authms.application.port.UserRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    /**
     * 業務日付は業務タイムゾーンで判断する。UTC で判断すると、時差の分だけ「当日」の扱いが
     * ずれる時間帯ができる。
     */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    @Bean
    public LoginUseCase loginUseCase(UserRepository users, AuthAuditLogger auditLogger,
            PasswordVerifier passwordVerifier, TokenIssuer tokenIssuer, Clock clock) {
        return new LoginUseCase(users, auditLogger, passwordVerifier, tokenIssuer, clock);
    }
}
