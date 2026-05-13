package com.example.cargotracker.authms.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    /** 5 回連続失敗でアカウントロック対象とする閾値。 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** ロック継続時間。経過後は自動解除される。 */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UserId id;
    private UserName username;
    private Email email;
    private PasswordHash passwordHash;
    private boolean enabled;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final Set<Role> roles;
    private int failedAttempts;
    private LocalDateTime lockUntil;

    @SuppressWarnings("java:S107")
    private User(UserId id, UserName username, Email email, PasswordHash passwordHash,
                 boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt, Set<Role> roles,
                 int failedAttempts, LocalDateTime lockUntil) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.roles = new HashSet<>(roles);
        this.failedAttempts = failedAttempts;
        this.lockUntil = lockUntil;
    }

    public static User create(UserName username, Email email, PasswordHash passwordHash) {
        if (username == null) {
            throw new IllegalArgumentException("username は必須です");
        }
        if (email == null) {
            throw new IllegalArgumentException("email は必須です");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("passwordHash は必須です");
        }
        var now = LocalDateTime.now();
        return new User(UserId.generate(), username, email, passwordHash, true, now, now,
                new HashSet<>(), 0, null);
    }

    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt,
                new HashSet<>(), 0, null);
    }

    @SuppressWarnings("java:S107")
    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, Set<Role> roles) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt, roles, 0, null);
    }

    @SuppressWarnings("java:S107")
    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, Set<Role> roles,
                                   int failedAttempts, LocalDateTime lockUntil) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt, roles,
                failedAttempts, lockUntil);
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void setRoles(Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ログイン失敗を記録する。
     *
     * <p>仕様（US00-r1）:</p>
     * <ul>
     *   <li>ロックが時刻経過で解除済みの場合は失敗カウンタを 0 にリセットしてから 1 回目として記録する</li>
     *   <li>失敗カウンタが {@link #MAX_FAILED_ATTEMPTS} に到達したら {@code now + LOCK_DURATION} を lockUntil に設定する</li>
     * </ul>
     */
    public void recordFailedAttempt(LocalDateTime now) {
        // 経過済みロックは解除してから再カウントする
        if (lockUntil != null && !now.isBefore(lockUntil)) {
            failedAttempts = 0;
            lockUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockUntil = now.plus(LOCK_DURATION);
        }
        updatedAt = now;
    }

    /**
     * ログイン成功時に失敗カウンタとロックをリセットする。
     */
    public void recordSuccessfulLogin() {
        this.failedAttempts = 0;
        this.lockUntil = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 指定時刻時点でアカウントがロック中かどうかを判定する。
     */
    public boolean isLocked(LocalDateTime now) {
        return lockUntil != null && now.isBefore(lockUntil);
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public UserId id() {
        return id;
    }

    public UserName username() {
        return username;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public LocalDateTime lockUntil() {
        return lockUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User u)) {
            return false;
        }
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
