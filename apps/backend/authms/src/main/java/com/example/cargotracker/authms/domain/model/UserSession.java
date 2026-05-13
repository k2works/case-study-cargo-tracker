package com.example.cargotracker.authms.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JWT トークンのセッションレコード。
 *
 * <p>US00-r2（ログアウト）: トークンの一意識別子 jti でセッションを管理し、
 * revoke 操作で無効化フラグを立てる。
 * JwtAuthenticationFilter が認証時に revoked を確認することでログアウトを実現する。</p>
 *
 * <p>不変条件: jti は token と 1 対 1。revoked は false → true の単方向遷移のみ。</p>
 */
public class UserSession {

    private final String jti;
    private final UserId userId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;
    private boolean revoked;

    private UserSession(String jti, UserId userId, LocalDateTime issuedAt,
                        LocalDateTime expiresAt, boolean revoked) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti は必須です");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId は必須です");
        }
        if (issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("issuedAt / expiresAt は必須です");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt は issuedAt より後である必要があります");
        }
        this.jti = jti;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }

    /** ログイン成功時に新規セッションを発行する。 */
    public static UserSession issue(String jti, UserId userId,
                                    LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return new UserSession(jti, userId, issuedAt, expiresAt, false);
    }

    /** 永続化された行から復元する。 */
    public static UserSession reconstruct(String jti, UserId userId,
                                          LocalDateTime issuedAt, LocalDateTime expiresAt,
                                          boolean revoked) {
        return new UserSession(jti, userId, issuedAt, expiresAt, revoked);
    }

    /** セッションを無効化する。冪等。 */
    public void revoke() {
        this.revoked = true;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public String jti() {
        return jti;
    }

    public UserId userId() {
        return userId;
    }

    public LocalDateTime issuedAt() {
        return issuedAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserSession s)) {
            return false;
        }
        return Objects.equals(jti, s.jti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jti);
    }
}
