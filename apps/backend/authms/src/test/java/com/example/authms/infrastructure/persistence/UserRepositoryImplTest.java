package com.example.authms.infrastructure.persistence;

import com.example.authms.domain.Role;
import com.example.authms.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserRepositoryImpl} のユニットテスト（Mapper への委譲を検証）。
 */
class UserRepositoryImplTest {

    private UserMapper mapper;
    private UserRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        repository = new UserRepositoryImpl(mapper);
    }

    @Test
    @DisplayName("findByUsername は Mapper に委譲する")
    void findByUsernameは委譲() {
        User user = new User(1L, "alice", "h", Role.ROLE_ADMIN, 0, null);
        when(mapper.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<User> result = repository.findByUsername("alice");

        assertThat(result).contains(user);
    }

    @Test
    @DisplayName("save は Mapper.insert を呼ぶ")
    void saveでinsertを呼ぶ() {
        User user = new User(null, "bob", "h", Role.ROLE_SALES, 0, null);

        repository.save(user);

        verify(mapper).insert(user);
    }

    @Test
    @DisplayName("updateFailedAttempts は Mapper に委譲する")
    void 失敗回数の更新() {
        repository.updateFailedAttempts("alice", 3);
        verify(mapper).updateFailedAttempts("alice", 3);
    }

    @Test
    @DisplayName("lockUser は現在時刻でロックする")
    void ユーザーロック() {
        repository.lockUser("alice");
        verify(mapper).lockUser(eq("alice"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("unlockUser は Mapper.unlockUser を呼ぶ")
    void ユーザーアンロック() {
        repository.unlockUser("alice");
        verify(mapper).unlockUser("alice");
    }
}
