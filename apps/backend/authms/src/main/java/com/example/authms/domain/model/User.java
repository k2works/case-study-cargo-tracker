package com.example.authms.domain.model;

import com.example.shared.auth.Role;
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
    private final LoginState loginState;
    private final Set<Role> roles;

    private User(Long id, String username, String email, String displayName, String passwordHash,
            boolean enabled, LoginState loginState, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.loginState = loginState;
        this.roles = Set.copyOf(roles);
    }

    /**
     * 永続化された状態から復元する。
     *
     * <p>ここでは業務的な検査を行わない。不変条件を後から足すと、その列が無かったころの行が
     * 読めなくなる。新規受け入れ時の検査は生成側で行う。
     */
    public static User restore(Long id, String username, String email, String displayName,
            String passwordHash, boolean enabled, LoginState loginState, Set<Role> roles) {
        return new User(id, username, email, displayName, passwordHash, enabled, loginState, roles);
    }

    /**
     * 利用を止められているか。
     *
     * <p>「無効化されている」の意味はここでだけ決める。呼び出し側が {@code !enabled()} と書くと、
     * 意味が変わったとき（退職・契約終了など条件が増えたとき）に片方だけが直る。
     */
    public boolean isDisabled() {
        return !enabled;
    }

    /** ログイン試行を受け付けられるか。無効化・ロック中はパスワードを照合するまでもなく拒否する。 */
    public boolean canAttemptLoginAt(Instant now) {
        if (isDisabled()) {
            return false;
        }
        // 期限ちょうどはまだロック中とみなす（境界で緩める理由がない）
        return lockedUntil() == null || now.isAfter(lockedUntil());
    }

    /**
     * 認証に失敗した状態を返す。閾値に達したらロックする。
     *
     * <p>ロック期限を過ぎていれば失敗回数を数え直す。持ち越すと、解除後の 1 回の誤入力で
     * 即座に再ロックされ、正規の利用者は事実上パスワードを 1 回も間違えられなくなる
     * （US31 の「一定時間の経過で自動解除される」が名目だけになる）。
     */
    public User withFailedAttemptAt(Instant now) {
        // 期限切れのロックは「無かったこと」にする。期限だけ残すと、次に数え直したときも
        // 期限切れと判定され続け、何度失敗してもロックされない
        boolean expired = isLockExpiredAt(now);
        int attempts = (expired ? 0 : failedAttempts()) + 1;
        Instant previousLock = expired ? null : lockedUntil();
        Instant lock = attempts >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : previousLock;
        return new User(id, username, email, displayName, passwordHash, enabled,
                new LoginState(attempts, lock), roles);
    }

    /** ロックされていた期限を過ぎているか。未ロックなら false（数え直す理由がない）。 */
    private boolean isLockExpiredAt(Instant now) {
        return lockedUntil() != null && now.isAfter(lockedUntil());
    }

    /** 認証に成功した状態を返す。連続失敗の数え直しとロック解除を同時に行う。 */
    public User withSuccessfulLogin() {
        return new User(
                id, username, email, displayName, passwordHash, enabled, LoginState.clean(), roles);
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

    /** ログインの試行状況。回数と期限は対で意味を持つ。 */
    public LoginState loginState() {
        return loginState;
    }

    public int failedAttempts() {
        return loginState.failedAttempts();
    }

    public Instant lockedUntil() {
        return loginState.lockedUntil();
    }

    public Set<Role> roles() {
        return roles;
    }
}
