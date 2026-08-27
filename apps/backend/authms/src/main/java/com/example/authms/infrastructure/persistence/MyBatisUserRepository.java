package com.example.authms.infrastructure.persistence;

import com.example.authms.application.port.UserRepository;
import com.example.shared.auth.Role;
import com.example.authms.domain.model.LoginState;
import com.example.authms.domain.model.User;
import com.example.authms.domain.model.UserIdentity;
import com.example.authms.domain.model.UserShipperLink;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(MyBatisUserRepository.class);

    /** 同時試行が競り合ったときのやり直し上限。ロックの閾値（5）を十分に上回る値にする。 */
    private static final int MAX_RETRIES = 20;

    private final UserMapper mapper;

    public MyBatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserRecord row = mapper.findByUsername(username);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(row));
    }

    @Override
    public java.util.List<User> findLocked(Instant now) {
        return mapper.findLocked(now).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Long> findLinkedShipperId(String username) {
        return Optional.ofNullable(mapper.findLinkedShipperId(username));
    }

    @Override
    public UserShipperLink saveShipperLink(UserShipperLink link) {
        mapper.deleteShipperLink(link.username());
        mapper.insertShipperLink(link.username(), link.shipperId());
        return link;
    }

    @Override
    public Optional<UserShipperLink> removeShipperLink(String username) {
        Optional<UserShipperLink> current = findLinkedShipperId(username)
                .map(shipperId -> new UserShipperLink(username, shipperId));
        current.ifPresent(ignored -> mapper.deleteShipperLink(username));
        return current;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private User toDomain(UserRecord row) {
        return User.restore(
                row.getId(),
                new UserIdentity(
                        row.getUsername(), row.getEmail(), row.getDisplayName(), row.getPassword()),
                row.isEnabled(),
                new LoginState(row.getFailedAttempts(), row.getLockedUntil()),
                rolesOf(row.getId()));
    }

    @Override
    public void updateLoginState(User user) {
        mapper.updateLoginState(user.id(), user.failedAttempts(), user.lockedUntil());
    }

    /**
     * 失敗回数を数える規則そのものは {@link User#withFailedAttemptAt} が持つ。ここが守るのは
     * 「読んだ時点から変わっていないこと」だけで、変わっていたら読み直してやり直す。
     *
     * <p>規則を SQL 側に書き写すと、ロックの閾値と期間の定義が 2 箇所に増える。
     */
    @Override
    public User recordFailedAttempt(User user, Instant now) {
        User current = user;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            User failed = current.withFailedAttemptAt(now);
            int updated = mapper.updateLoginStateIfUnchanged(
                    failed.id(), failed.failedAttempts(), failed.lockedUntil(),
                    current.failedAttempts());
            if (updated == 1) {
                return failed;
            }
            // 誰かが先に数えた。その結果の上に数え直す
            current = findByUsername(user.username()).orElse(current);
        }
        // ここへ来るのは同時試行が MAX_RETRIES 回続けて競り勝った場合だけ。
        // 数え損ねるより「今の状態」を返して先へ進めるほうが安全側に倒れる
        if (log.isWarnEnabled()) {
            log.warn("失敗回数の記録が {} 回競合しました: {}", MAX_RETRIES, user.username());
        }
        return findByUsername(user.username()).orElse(current);
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
