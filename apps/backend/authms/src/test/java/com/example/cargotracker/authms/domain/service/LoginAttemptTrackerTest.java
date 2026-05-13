package com.example.cargotracker.authms.domain.service;

import com.example.cargotracker.authms.domain.model.Email;
import com.example.cargotracker.authms.domain.model.PasswordHash;
import com.example.cargotracker.authms.domain.model.User;
import com.example.cargotracker.authms.domain.model.UserName;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("LoginAttemptTracker（ドメインサービス）")
class LoginAttemptTrackerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 13, 10, 0);

    private UserRepository userRepository;
    private Clock fixedClock;
    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        tracker = new LoginAttemptTracker(userRepository, fixedClock);
    }

    private User newUser() {
        return User.create(
                new UserName("alice"),
                new Email("alice@example.com"),
                new PasswordHash("$2a$10$hashed"));
    }

    @Test
    @DisplayName("recordFailure を呼ぶと failedAttempts が増えて update される")
    void 失敗記録で失敗カウンタが増えて永続化される() {
        var user = newUser();

        tracker.recordFailure(user);

        assertThat(user.failedAttempts()).isEqualTo(1);
        verify(userRepository).update(user);
    }

    @Test
    @DisplayName("5 回 recordFailure を呼ぶと lockUntil が NOW + 30 分に設定される")
    void 五回失敗でロック時刻が永続化される() {
        var user = newUser();

        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(user);
        }

        assertThat(user.failedAttempts()).isEqualTo(5);
        assertThat(user.lockUntil()).isEqualTo(NOW.plusMinutes(30));
        verify(userRepository, org.mockito.Mockito.times(5)).update(user);
    }

    @Test
    @DisplayName("recordSuccess を呼ぶと失敗カウンタとロックがリセットされて update される")
    void 成功記録でリセットされて永続化される() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }

        tracker.recordSuccess(user);

        assertThat(user.failedAttempts()).isZero();
        assertThat(user.lockUntil()).isNull();
        verify(userRepository).update(user);
    }

    @Test
    @DisplayName("isLocked は Clock の現在時刻で User.isLocked を判定する")
    void ロック判定はClockの現在時刻で行う() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }

        assertThat(tracker.isLocked(user)).isTrue();
    }

    @Test
    @DisplayName("Clock を進めるとロックが自動解除される")
    void Clock経過後はロック解除される() {
        var user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedAttempt(NOW);
        }
        // Clock を 31 分先に進めた tracker を別途生成して確認
        var afterUnlock = NOW.plusMinutes(31);
        var clockAfter = Clock.fixed(afterUnlock.atZone(ZONE).toInstant(), ZONE);
        var trackerAfter = new LoginAttemptTracker(userRepository, clockAfter);

        assertThat(trackerAfter.isLocked(user)).isFalse();
    }
}
