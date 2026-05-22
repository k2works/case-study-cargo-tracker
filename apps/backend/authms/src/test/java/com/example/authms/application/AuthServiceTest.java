package com.example.authms.application;

import com.example.authms.domain.Role;
import com.example.authms.domain.User;
import com.example.authms.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void 正しい認証情報でログインできる() {
        String rawPassword = "password123";
        User user = new User(1L, "admin01", passwordEncoder.encode(rawPassword),
                Role.ROLE_ADMIN, 0, null);
        when(userRepository.findByUsername("admin01")).thenReturn(Optional.of(user));

        User result = authService.authenticate("admin01", rawPassword);

        assertThat(result.getUsername()).isEqualTo("admin01");
        verify(userRepository).updateFailedAttempts("admin01", 0);
    }

    @Test
    void 存在しないユーザーで認証例外が発生する() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate("unknown", "pass"))
                .isInstanceOf(AuthService.AuthenticationException.class);
    }

    @Test
    void パスワード誤りで失敗カウントが増加する() {
        User user = new User(1L, "user01", passwordEncoder.encode("correct"),
                Role.ROLE_SHIPPER, 0, null);
        when(userRepository.findByUsername("user01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("user01", "wrong"))
                .isInstanceOf(AuthService.AuthenticationException.class);

        verify(userRepository).updateFailedAttempts("user01", 1);
    }

    @Test
    void パスワード5回誤りでアカウントがロックされる() {
        User user = new User(1L, "user01", passwordEncoder.encode("correct"),
                Role.ROLE_SHIPPER, 4, null);
        when(userRepository.findByUsername("user01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("user01", "wrong"))
                .isInstanceOf(AuthService.AccountLockedException.class);

        verify(userRepository).lockUser("user01");
    }

    @Test
    void ロック済みアカウントでログインするとAccountLockedExceptionが発生する() {
        User user = new User(1L, "locked01", passwordEncoder.encode("pass"),
                Role.ROLE_SHIPPER, 5, java.time.LocalDateTime.now());
        when(userRepository.findByUsername("locked01")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("locked01", "pass"))
                .isInstanceOf(AuthService.AccountLockedException.class);
    }
}
