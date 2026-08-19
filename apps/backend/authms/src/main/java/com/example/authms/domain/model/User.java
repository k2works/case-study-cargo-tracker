package com.example.authms.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 利用者。認証の可否とアカウント保護（US31）の判断を担う集約ルート。
 *
 * <p>失敗回数とロック期限は永続化された状態から復元する。監査ログから再導出すると、
 * ログの欠落や集計の取りこぼしで「ロックされていないこと」になり、保護が静かに外れる。
 */
public final class User {

    /** 連続失敗をロックとみなす回数（US31）。 */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** ロックの継続時間。経過後は解除操作なしで再び受け付ける。 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Long id;
    private final String username;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final int failedAttempts;
    private final Instant lockedUntil;
    private final Set<Role> roles;

    private User(Long id, String username, String email, String displayName, String passwordHash,
            boolean enabled, int failedAttempts, Instant lockedUntil, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.roles = Set.copyOf(roles);
    }

    /**
     * 永続化された状態から復元する。
     *
     * <p>ここでは業務的な検査を行わない。不変条件を後から足すと、その列が無かったころの行が
     * 読めなくなる。新規受け入れ時の検査は生成側で行う。
     */
    public static User restore(Long id, String username, String email, String displayName,
            String passwordHash, boolean enabled, int failedAttempts, Instant lockedUntil,
            Set<Role> roles) {
        return new User(id, username, email, displayName, passwordHash, enabled, failedAttempts,
                lockedUntil, roles);
    }

    /** ログイン試行を受け付けられるか。無効化・ロック中はパスワードを照合するまでもなく拒否する。 */
    public boolean canAttemptLoginAt(Instant now) {
        if (!enabled) {
            return false;
        }
        // 期限ちょうどはまだロック中とみなす（境界で緩める理由がない）
        return lockedUntil == null || now.isAfter(lockedUntil);
    }

    /** 認証に失敗した状態を返す。閾値に達したらロックする。 */
    public User withFailedAttemptAt(Instant now) {
        int attempts = failedAttempts + 1;
        Instant lock = attempts >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : lockedUntil;
        return new User(id, username, email, displayName, passwordHash, enabled, attempts, lock, roles);
    }

    /** 認証に成功した状態を返す。連続失敗の数え直しとロック解除を同時に行う。 */
    public User withSuccessfulLogin() {
        return new User(id, username, email, displayName, passwordHash, enabled, 0, null, roles);
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return enabled;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Set<Role> roles() {
        return roles;
    }
}
