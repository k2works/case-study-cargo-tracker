package com.example.cargotracker.authms.domain.service;

import com.example.cargotracker.authms.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT トークンの失効を管理するドメインサービス（US00-r2）。
 *
 * <p>{@code user_sessions.revoked} を TRUE に更新することで、
 * 以降の認証で {@code JwtAuthenticationFilter} が拒否する。</p>
 */
@Service
@Transactional
public class TokenRevocationService {

    private final UserSessionRepository userSessionRepository;

    public TokenRevocationService(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    /**
     * 指定 jti のセッションを無効化する。冪等。
     */
    public void revoke(String jti) {
        userSessionRepository.revokeByJti(jti);
    }
}
