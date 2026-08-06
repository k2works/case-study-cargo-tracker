package com.example.cargotracker.security.infrastructure.repositories;

import com.example.cargotracker.security.domain.model.Role;
import com.example.cargotracker.security.domain.model.UserAccount;
import com.example.cargotracker.security.domain.repository.UserAccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** {@link UserAccountRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisUserAccountRepository implements UserAccountRepository {

    private static final Logger LOG =
            LoggerFactory.getLogger(MyBatisUserAccountRepository.class);

    private final UserAccountMapper mapper;

    public MyBatisUserAccountRepository(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return toAggregate(mapper.findByUsername(username));
    }

    @Override
    public Optional<UserAccount> findByUsernameForUpdate(String username) {
        return toAggregate(mapper.findByUsernameForUpdate(username));
    }

    @Override
    public void updateLockState(UserAccount account) {
        int updated =
                mapper.updateLockState(account.id(), account.failedAttempts(), account.lockedUntil());
        if (updated != 1) {
            // 更新件数を捨てると、対象が消えていても成功として扱ってしまう。
            // ロックの記録が落ちたことに気づけないのは、ロックが無いのと同じである。
            throw new IllegalStateException(
                    "ロック状態を更新できませんでした: userId=" + account.id());
        }
    }

    private Optional<UserAccount> toAggregate(UserAccountRecord row) {
        if (row == null) {
            return Optional.empty();
        }
        Set<Role> roles = mapper.findRoles(row.getId()).stream()
                .map(MyBatisUserAccountRepository::toRole)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new UserAccount(
                new UserAccount.Identity(
                        row.getId(), row.getUsername(),
                        row.getEmail(), row.getPassword()),
                row.isEnabled(),
                roles,
                row.getFailedAttempts(),
                row.getLockedUntil()));
    }

    /**
     * DB のロール文字列を列挙型へ変換する。
     *
     * <p>未知のロールは除外し、ログに残す。{@code valueOf} で例外にすると、
     * DB にロールが 1 つ増えただけで既存利用者が全員ログインできなくなる。
     */
    private static Optional<Role> toRole(String authority) {
        try {
            return Optional.of(Role.valueOf(authority.replace("ROLE_", "")));
        } catch (IllegalArgumentException e) {
            LOG.warn("未知のロールを無視しました authority={}", authority);
            return Optional.empty();
        }
    }
}
