package com.example.authms.infrastructure.persistence;

import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.Role;
import com.example.authms.domain.model.User;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MyBatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserRecord record = mapper.findByUsername(username);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(User.restore(
                record.getId(),
                record.getUsername(),
                record.getEmail(),
                record.getDisplayName(),
                record.getPassword(),
                record.isEnabled(),
                record.getFailedAttempts(),
                record.getLockedUntil(),
                rolesOf(record.getId())));
    }

    @Override
    public void updateLoginState(User user) {
        mapper.updateLoginState(user.id(), user.failedAttempts(), user.lockedUntil());
    }

    private Set<Role> rolesOf(Long userId) {
        return mapper.findRolesByUserId(userId).stream()
                // 未知のロール名は捨てる。列挙にない値で例外にすると、ロール追加の途中段階で
                // 既存利用者までログインできなくなる
                .filter(MyBatisUserRepository::isKnownRole)
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isKnownRole(String name) {
        for (Role role : Role.values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
