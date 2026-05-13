package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthCommandService")
class AuthCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthCommandService service;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        service = new AuthCommandService(userRepository, encoder);
    }

    @Test
    @DisplayName("新規ユーザーを登録できる")
    void 新規ユーザーを登録できる() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);

        service.register("alice", "alice@example.com", "Password1!");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("既存ユーザー名は拒否する")
    void 既存ユーザー名は拒否する() {
        when(userRepository.existsByUsername(any())).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "alice@example.com", "Password1!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("既に使用されています");
    }
}
