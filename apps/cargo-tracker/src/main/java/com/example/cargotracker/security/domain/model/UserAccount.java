package com.example.cargotracker.security.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * 認証アカウント。
 *
 * <p>ロック状態は<strong>集約が保持する</strong>（US31）。失敗回数を認証ログから
 * 数え直す設計にすると、ユニットテストが緑でもリクエストをまたいだ時に誤判定する。
 * 「5 回連続失敗でロック」はリクエストを越えて成立しなければならない不変条件である。
 *
 * <p>Security は業務ドメインではなく支援サブドメインであるため、共有カーネルではなく
 * {@code shared} 配下に置く。
 */
public final class UserAccount {

    /** ロックまでに許容する連続失敗回数。 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** ロックの継続時間。 */
    public static final java.time.Duration LOCK_DURATION = java.time.Duration.ofMinutes(30);

    private final Long id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<Role> roles;
    private int failedAttempts;
    private Instant lockedUntil;

    /**
     * 識別情報。引数の数を抑えるためにまとめる。
     *
     * @param id           サロゲートキー
     * @param username     利用者 ID
     * @param email        メールアドレス
     * @param passwordHash BCrypt ハッシュ
     */
    public record Identity(Long id, String username, String email, String passwordHash) {
    }

    public UserAccount(
            Identity identity,
            boolean enabled,
            Set<Role> roles,
            int failedAttempts,
            Instant lockedUntil) {
        this.id = identity.id();
        this.username = identity.username();
        this.email = identity.email();
        this.passwordHash = identity.passwordHash();
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    /**
     * 指定時刻においてロック中かを返す。
     *
     * @param now 判定基準時刻
     * @return ロック中なら true
     */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * 認証失敗を記録する。
     *
     * <p>失敗回数が上限に達したらロックする。
     *
     * <p><strong>ロック期限が切れていたら回数を数え直す。</strong> 不変条件は
     * 「{@value #MAX_FAILED_ATTEMPTS} 回<em>連続</em>で失敗したらロックする」であり、
     * 期限切れ後も回数を持ち越すと、30 分待って復帰した利用者が 1 回打ち間違えただけで
     * 再び 30 分締め出される。それは総当たり攻撃への防御ではなく、正当な利用者への妨害である。
     *
     * @param now 失敗した時刻
     */
    public void recordFailure(Instant now) {
        if (lockedUntil != null && !isLockedAt(now)) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts += 1;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    /** 認証成功を記録する。失敗回数とロックを解除する。 */
    public void recordSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /** 管理者によるロック解除。 */
    public void unlock() {
        recordSuccess();
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

    public String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return enabled;
    }

    public Set<Role> roles() {
        return roles;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
