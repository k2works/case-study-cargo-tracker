package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthQueryService")
class AuthQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthQueryService service;

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        var jwtProvider = new JwtTokenProvider(
                "test-secret-key-at-least-32-characters-long",
                86400000L
        );
        service = new AuthQueryService(userRepository, encoder, jwtProvider);
    }

    @Test
    @DisplayName("正しい資格情報でトークンを取得できる")
    void 正しい資格情報でトークンを取得できる() {
        String rawPassword = "Password1!";
        var user = User.create(
                new UserName("alice"),
                new Email("alice@example.com"),
                new PasswordHash(encoder.encode(rawPassword))
        );
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));

        String token = service.login("alice", rawPassword);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("存在しないユーザーはエラーになる")
    void 存在しないユーザーはエラーになる() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody", "pass"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("パスワード不一致はエラーになる")
    void パスワード不一致はエラーになる() {
        var user = User.create(
                new UserName("alice"),
                new Email("alice@example.com"),
                new PasswordHash(encoder.encode("correct"))
        );
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
