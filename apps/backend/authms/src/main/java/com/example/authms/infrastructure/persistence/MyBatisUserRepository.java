package com.example.authms.infrastructure.persistence;

import com.example.authms.application.port.UserRepository;
import com.example.shared.auth.Role;
import com.example.authms.domain.model.User;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(MyBatisUserRepository.class);

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
                .map(this::resolveRole)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * ロール名を解決する。
     *
     * <p>未知の名前で例外にすると、ロール追加の途中段階で既存利用者までログインできなくなる。
     * ただし黙って捨てると、打ち間違いが「権限が足りない」という別の症状として現れるため、
     * 捨てたことは記録に残す。
     */
    private Optional<Role> resolveRole(String name) {
        Optional<Role> role = Role.of(name);
        if (role.isEmpty()) {
            log.warn("未知のロール名を無視しました: {}", name);
        }
        return role;
    }
}
