package com.example.cargotracker.security.infrastructure.repositories;

import com.example.cargotracker.security.domain.model.Role;
import com.example.cargotracker.security.domain.model.UserAccount;
import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link UserAccountRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisUserAccountRepository implements UserAccountRepository {

    private final UserAccountMapper mapper;

    public MyBatisUserAccountRepository(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        UserAccountRecord record = mapper.findByUsername(username);
        if (record == null) {
            return Optional.empty();
        }
        Set<Role> roles = mapper.findRoles(record.getId()).stream()
                .map(r -> Role.valueOf(r.replace("ROLE_", "")))
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new UserAccount(
                new UserAccount.Identity(
                        record.getId(), record.getUsername(),
                        record.getEmail(), record.getPassword()),
                record.isEnabled(),
                roles,
                record.getFailedAttempts(),
                record.getLockedUntil()));
    }

    @Override
    public void updateLockState(UserAccount account) {
        mapper.updateLockState(account.id(), account.failedAttempts(), account.lockedUntil());
    }
}
