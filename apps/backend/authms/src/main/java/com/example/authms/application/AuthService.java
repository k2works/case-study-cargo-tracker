package com.example.authms.application;

import com.example.authms.domain.User;
import com.example.authms.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("ユーザーが見つかりません"));

        if (user.isLocked()) {
            throw new AccountLockedException("アカウントがロックされています");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            user.incrementFailedAttempts();
            userRepository.updateFailedAttempts(username, user.getFailedLoginAttempts());
            if (user.isLocked()) {
                userRepository.lockUser(username);
                throw new AccountLockedException("ログイン失敗が 5 回に達したためアカウントをロックしました");
            }
            throw new AuthenticationException("パスワードが正しくありません");
        }

        user.resetFailedAttempts();
        userRepository.updateFailedAttempts(username, 0);
        return user;
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) { super(message); }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) { super(message); }
    }
}
