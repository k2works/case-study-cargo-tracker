package com.example.cargotracker.authms.infrastructure.persistence;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.domain.repository.UserSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local-h2")
@Transactional
@DisplayName("UserSessionRepository（MyBatis）統合テスト")
class UserSessionRepositoryTest {

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private UserId persistUser() {
        var user = User.create(
                new UserName("session-test-" + UUID.randomUUID().toString().substring(0, 8)),
                new Email("st-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com"),
                new PasswordHash("$2a$10$hashed"));
        userRepository.save(user);
        return user.id();
    }

    @Test
    @DisplayName("セッションを保存して jti で取得できる")
    void セッションを保存して取得できる() {
        var userId = persistUser();
        String jti = UUID.randomUUID().toString();
        var now = LocalDateTime.of(2026, 5, 13, 10, 0);
        var session = UserSession.issue(jti, userId, now, now.plusHours(1));

        sessionRepository.save(session);

        var found = sessionRepository.findByJti(jti);
        assertThat(found).isPresent();
        assertThat(found.get().jti()).isEqualTo(jti);
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().issuedAt()).isEqualTo(now);
        assertThat(found.get().expiresAt()).isEqualTo(now.plusHours(1));
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("存在しない jti の取得は空を返す")
    void 存在しないjtiは空を返す() {
        var found = sessionRepository.findByJti(UUID.randomUUID().toString());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("revokeByJti でセッションを無効化できる")
    void セッションを無効化できる() {
        var userId = persistUser();
        String jti = UUID.randomUUID().toString();
        var now = LocalDateTime.of(2026, 5, 13, 10, 0);
        sessionRepository.save(UserSession.issue(jti, userId, now, now.plusHours(1)));

        sessionRepository.revokeByJti(jti);

        var found = sessionRepository.findByJti(jti);
        assertThat(found).isPresent();
        assertThat(found.get().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("isRevoked は存在しない jti に対して false を返す（明示的失効のみ true）")
    void 存在しないjtiは失効扱いしない() {
        assertThat(sessionRepository.isRevoked(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("isRevoked は未失効セッションに対して false を返す")
    void 未失効セッションはfalse() {
        var userId = persistUser();
        String jti = UUID.randomUUID().toString();
        var now = LocalDateTime.of(2026, 5, 13, 10, 0);
        sessionRepository.save(UserSession.issue(jti, userId, now, now.plusHours(1)));

        assertThat(sessionRepository.isRevoked(jti)).isFalse();
    }
}
