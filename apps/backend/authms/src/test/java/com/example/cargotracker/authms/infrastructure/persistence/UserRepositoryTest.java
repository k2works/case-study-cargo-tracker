package com.example.cargotracker.authms.infrastructure.persistence;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local-h2")
@Transactional
@DisplayName("UserRepository（MyBatis）統合テスト")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    @DisplayName("ユーザーを保存して ID で取得できる")
    void ユーザーを保存してIDで取得できる() {
        var user = User.create(
                new UserName("bob"),
                new Email("bob@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        repository.save(user);

        var found = repository.findById(user.id());
        assertThat(found).isPresent();
        assertThat(found.get().username().value()).isEqualTo("bob");
    }

    @Test
    @DisplayName("ユーザー名で検索できる")
    void ユーザー名で検索できる() {
        var user = User.create(
                new UserName("carol"),
                new Email("carol@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        repository.save(user);

        var found = repository.findByUsername(new UserName("carol"));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("存在しないユーザーは空を返す")
    void 存在しないユーザーは空を返す() {
        var found = repository.findByUsername(new UserName("nobody"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("ユーザー名の存在確認ができる")
    void ユーザー名の存在確認ができる() {
        var user = User.create(
                new UserName("dave"),
                new Email("dave@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        repository.save(user);

        assertThat(repository.existsByUsername(new UserName("dave"))).isTrue();
        assertThat(repository.existsByUsername(new UserName("nobody"))).isFalse();
    }

    @Test
    @DisplayName("新規ユーザーは failedAttempts=0 で永続化される")
    void 新規ユーザーは失敗カウンタ0で保存される() {
        var user = User.create(
                new UserName("eve"),
                new Email("eve@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        repository.save(user);

        var found = repository.findByUsername(new UserName("eve"));
        assertThat(found).isPresent();
        assertThat(found.get().failedAttempts()).isZero();
        assertThat(found.get().lockUntil()).isNull();
    }

    @Test
    @DisplayName("failedAttempts と lockUntil が update で永続化される")
    void 失敗カウンタとロック時刻が永続化される() {
        var user = User.create(
                new UserName("frank"),
                new Email("frank@example.com"),
                new PasswordHash("$2a$10$hashed")
        );
        repository.save(user);

        // 5 回失敗を記録
        var now = LocalDateTime.of(2026, 5, 13, 10, 0);
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(now);
        }
        repository.update(user);

        var found = repository.findByUsername(new UserName("frank"));
        assertThat(found).isPresent();
        assertThat(found.get().failedAttempts()).isEqualTo(5);
        assertThat(found.get().lockUntil()).isEqualTo(now.plusMinutes(30));
        assertThat(found.get().isLocked(now)).isTrue();
    }
}
