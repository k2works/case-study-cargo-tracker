package com.example.authms.infrastructure.repositories;

import com.example.authms.domain.model.aggregates.User;
import com.example.authms.domain.model.valueobjects.Email;
import com.example.authms.domain.model.valueobjects.Password;
import com.example.authms.domain.model.valueobjects.Role;
import com.example.authms.domain.model.valueobjects.UserName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisUserRepositoryTest {

    @Test
    void findByIdでユーザーを取得できる() {
        StubUserMapper userMapper = new StubUserMapper();
        userMapper.userRecord = userRecord(1L, "alice", "alice@example.com");
        userMapper.roles = List.of(roleRecord(10L, "ROLE_ADMIN"));
        MyBatisUserRepository repository = new MyBatisUserRepository(userMapper);

        Optional<User> user = repository.findById(1L);

        assertThat(user).isPresent();
        assertThat(user.orElseThrow().getUsername().getValue()).isEqualTo("alice");
    }

    @Test
    void saveは存在するroleのみ関連付ける() {
        StubUserMapper userMapper = new StubUserMapper();
        userMapper.selectedRole = null;
        MyBatisUserRepository repository = new MyBatisUserRepository(userMapper);
        User user = new User(new UserName("alice"), new Email("alice@example.com"),
                Password.fromEncoded("$2a$10$abcdefghijklmnopqrstuv"));
        user.addRole(Role.ROLE_ADMIN);

        repository.save(user);

        assertThat(userMapper.insertedUserRecord).isNotNull();
        assertThat(userMapper.insertedUserRoleCalls).isEmpty();
    }

    private static UserRecord userRecord(Long id, String username, String email) {
        UserRecord userRecord = new UserRecord();
        userRecord.setId(id);
        userRecord.setUsername(username);
        userRecord.setEmail(email);
        userRecord.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        userRecord.setEnabled(true);
        userRecord.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return userRecord;
    }

    private static RoleRecord roleRecord(Long id, String name) {
        RoleRecord roleRecord = new RoleRecord();
        roleRecord.setId(id);
        roleRecord.setName(name);
        return roleRecord;
    }

    private static final class StubUserMapper implements UserMapper {
        private UserRecord userRecord;
        private List<RoleRecord> roles = List.of();
        private RoleRecord selectedRole;
        private UserRecord insertedUserRecord;
        private final List<String> insertedUserRoleCalls = new java.util.ArrayList<>();

        @Override
        public UserRecord selectByUsername(String username) {
            return userRecord;
        }

        @Override
        public UserRecord selectByEmail(String email) {
            return userRecord;
        }

        @Override
        public UserRecord selectById(Long id) {
            return userRecord;
        }

        @Override
        public void insertUser(UserRecord userRecord) {
            this.insertedUserRecord = userRecord;
            userRecord.setId(100L);
        }

        @Override
        public List<RoleRecord> selectRolesByUserId(Long userId) {
            return roles;
        }

        @Override
        public void insertUserRole(Long userId, Long roleId) {
            insertedUserRoleCalls.add(userId + ":" + roleId);
        }

        @Override
        public RoleRecord selectRoleByName(String name) {
            return selectedRole;
        }
    }
}
