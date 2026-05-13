package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserCommandService ユニットテスト")
class AdminUserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserCommandService service;

    private User createTestUser() {
        return User.reconstruct(
                new UserId("test-id"),
                new UserName("testuser"),
                new Email("test@example.com"),
                new PasswordHash("hashed"),
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                Set.of(Role.ROLE_SHIPPER)
        );
    }

    @Test
    @DisplayName("ユーザーのロールを更新できる")
    void ユーザーのロールを更新できる() {
        var user = createTestUser();
        when(userRepository.findById(new UserId("test-id"))).thenReturn(Optional.of(user));

        service.updateRoles("test-id", List.of("ROLE_ADMIN", "ROLE_SALES"));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.ROLE_ADMIN, Role.ROLE_SALES);
        verify(userRepository).update(user);
    }

    @Test
    @DisplayName("存在しないユーザーのロール更新は例外を投げる")
    void 存在しないユーザーのロール更新は例外を投げる() {
        when(userRepository.findById(new UserId("missing"))).thenReturn(Optional.empty());
        var adminRoles = List.of("ROLE_ADMIN");

        assertThatThrownBy(() -> service.updateRoles("missing", adminRoles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ユーザーが見つかりません");
    }

    @Test
    @DisplayName("ユーザーを無効化できる")
    void ユーザーを無効化できる() {
        var user = createTestUser();
        when(userRepository.findById(new UserId("test-id"))).thenReturn(Optional.of(user));

        service.updateStatus("test-id", false);

        assertThat(user.isEnabled()).isFalse();
        verify(userRepository).update(user);
    }

    @Test
    @DisplayName("ユーザーを有効化できる")
    void ユーザーを有効化できる() {
        var user = User.reconstruct(
                new UserId("test-id"),
                new UserName("testuser"),
                new Email("test@example.com"),
                new PasswordHash("hashed"),
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                Set.of(Role.ROLE_SHIPPER)
        );
        when(userRepository.findById(new UserId("test-id"))).thenReturn(Optional.of(user));

        service.updateStatus("test-id", true);

        assertThat(user.isEnabled()).isTrue();
        verify(userRepository).update(user);
    }
}
