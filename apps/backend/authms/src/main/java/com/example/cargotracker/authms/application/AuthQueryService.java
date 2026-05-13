package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.AccountLockedException;
import com.example.cargotracker.authms.domain.model.Role;
import com.example.cargotracker.authms.domain.model.User;
import com.example.cargotracker.authms.domain.model.UserName;
import com.example.cargotracker.authms.domain.model.UserSession;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.domain.repository.UserSessionRepository;
import com.example.cargotracker.authms.domain.service.LoginAttemptTracker;
import com.example.cargotracker.authms.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthQueryService {

    private static final String INVALID_CREDENTIAL_MESSAGE = "ユーザー名またはパスワードが正しくありません";
    private static final String ACCOUNT_LOCKED_MESSAGE = "アカウントがロックされています。しばらく時間をおいてから再度お試しください。";

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptTracker loginAttemptTracker;

    public AuthQueryService(UserRepository userRepository, UserSessionRepository userSessionRepository,
                            PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
                            LoginAttemptTracker loginAttemptTracker) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptTracker = loginAttemptTracker;
    }

    /**
     * ログイン処理。失敗・成功を {@link LoginAttemptTracker} に記録する書き込みを伴うため、
     * メソッド固有のトランザクション（writable）でクラスレベルの readOnly を上書きする。
     */
    @Transactional
    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(new UserName(username))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIAL_MESSAGE));

        // アカウントロック判定（US00-r1）: ロック中なら 423 にマッピングする例外を投げる
        if (loginAttemptTracker.isLocked(user)) {
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }

        if (!passwordEncoder.matches(rawPassword, user.passwordHash().value())) {
            // 仕様（US00-r1）: 5 回目失敗時点でロックは成立するが、その回のレスポンスは 401。
            // 次回以降の試行が冒頭のロック判定で 423 を返す。
            loginAttemptTracker.recordFailure(user);
            throw new IllegalArgumentException(INVALID_CREDENTIAL_MESSAGE);
        }

        loginAttemptTracker.recordSuccess(user);
        var roles = user.getRoles().stream().map(Role::name).toList();
        String token = jwtTokenProvider.generateToken(user.username().value(), roles);

        // US00-r2: ログイン成功時に user_sessions レコードを作成し、ログアウト管理の起点とする
        var session = UserSession.issue(
                jwtTokenProvider.getJtiFromToken(token),
                user.id(),
                jwtTokenProvider.getIssuedAtFromToken(token),
                jwtTokenProvider.getExpirationFromToken(token));
        userSessionRepository.save(session);

        return token;
    }
}
