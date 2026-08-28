package com.example.authms.application.internal;

import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.application.port.PasswordVerifier;
import com.example.authms.application.port.TokenIssuer;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.AuthEventType;
import com.example.authms.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * ログイン。
 *
 * <p>失敗の理由（利用者が存在しない・パスワードが違う・ロック中・無効化）を呼び出し元に
 * 区別させない。区別できると画面や応答時間の差から「その利用者 ID は存在する」ことが漏れる（US31）。
 * 何が起きたかは監査ログにだけ残す。
 */
@Service
public class LoginUseCase {

    private final UserRepository users;
    private final AuthAuditLogger auditLogger;
    private final PasswordVerifier passwordVerifier;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public LoginUseCase(UserRepository users, AuthAuditLogger auditLogger,
            PasswordVerifier passwordVerifier, TokenIssuer tokenIssuer, Clock clock) {
        this.users = users;
        this.auditLogger = auditLogger;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    public Optional<LoginResult> login(String username, String rawPassword) {
        Instant now = clock.instant();
        Optional<User> found = users.findByUsername(username);

        if (found.isEmpty()) {
            // 未登録の利用者名での試行も残す。攻撃の兆候はここにしか現れない
            auditLogger.log(username, AuthEventType.LOGIN_FAILURE, "利用者が存在しない");
            return Optional.empty();
        }

        User user = found.get();

        if (user.isDisabled()) {
            auditLogger.log(username, AuthEventType.DISABLED_ATTEMPT, "無効化されたアカウント");
            return Optional.empty();
        }

        if (!user.canAttemptLoginAt(now)) {
            // ロック中は失敗回数を積まない。積むと解除直後に再ロックする状態が続き、
            // 正規の利用者がいつまでも入れなくなる
            auditLogger.log(username, AuthEventType.LOGIN_FAILURE, "ロック中の試行");
            return Optional.empty();
        }

        if (!passwordVerifier.matches(rawPassword, user.passwordHash())) {
            User failed = users.recordFailedAttempt(user, now);
            auditLogger.log(username, AuthEventType.LOGIN_FAILURE, "パスワード不一致");
            if (!failed.canAttemptLoginAt(now)) {
                auditLogger.log(username, AuthEventType.LOCKED, "連続失敗によるロック");
            }
            return Optional.empty();
        }

        users.updateLoginState(user.withSuccessfulLogin());
        auditLogger.log(username, AuthEventType.LOGIN_SUCCESS, null);
        return Optional.of(new LoginResult(
                tokenIssuer.issue(user), user.username(), user.displayName(), user.roles()));
    }
}
