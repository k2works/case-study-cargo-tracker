package com.example.cargotracker.authms.domain.service;

import com.example.cargotracker.authms.domain.model.User;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * ログイン試行（成功・失敗）を記録するドメインサービス。
 *
 * <p>US00-r1（アカウントロック）: 5 回連続失敗で 30 分ロック。詳細は {@link User} の不変条件を参照。</p>
 *
 * <p>{@link Clock} を注入することでテスト時に時刻を固定できる。</p>
 */
@Service
@Transactional
public class LoginAttemptTracker {

    private final UserRepository userRepository;
    private final Clock clock;

    public LoginAttemptTracker(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * ログイン失敗を記録する。User の失敗カウンタを更新して永続化する。
     */
    public void recordFailure(User user) {
        user.recordFailedAttempt(LocalDateTime.now(clock));
        userRepository.update(user);
    }

    /**
     * ログイン成功を記録する。User の失敗カウンタとロックをリセットして永続化する。
     */
    public void recordSuccess(User user) {
        user.recordSuccessfulLogin();
        userRepository.update(user);
    }

    /**
     * 指定 User が現在時刻でロック中かどうかを判定する。
     */
    public boolean isLocked(User user) {
        return user.isLocked(LocalDateTime.now(clock));
    }
}
