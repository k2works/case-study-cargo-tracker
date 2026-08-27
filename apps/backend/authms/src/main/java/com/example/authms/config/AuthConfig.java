package com.example.authms.config;

import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.internal.UnlockAccountUseCase;
import com.example.authms.application.internal.FindUserShipperLinkUseCase;
import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.PasswordVerifier;
import com.example.authms.application.port.TokenIssuer;
import com.example.authms.application.port.UserRepository;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.util.List;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AuthConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>ログイン・ログアウトはヘッダが付く前の入口であり、認証不要で開く。開くのはこの接頭辞だけで、
     * authms に将来足される他のエンドポイント（利用者管理など）は既定で守られる。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration = new FilterRegistrationBean<>(
                new AuthenticatedUserFilter(List.of("/api/v1/auth")));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

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

    @Bean
    public UnlockAccountUseCase unlockAccountUseCase(UserRepository users,
            AuthAuditLogger auditLogger, Clock clock) {
        return new UnlockAccountUseCase(users, auditLogger, clock);
    }

    @Bean
    public FindUserShipperLinkUseCase findUserShipperLinkUseCase(UserRepository users) {
        return new FindUserShipperLinkUseCase(users);
    }
}
