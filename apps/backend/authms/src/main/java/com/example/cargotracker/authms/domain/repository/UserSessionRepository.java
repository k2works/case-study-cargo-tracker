package com.example.cargotracker.authms.domain.repository;

import com.example.cargotracker.authms.domain.model.UserSession;

import java.util.Optional;

/**
 * UserSession の永続化を担う Repository（US00-r2 ログアウト）。
 */
public interface UserSessionRepository {

    /** 新規セッションを永続化する。 */
    void save(UserSession session);

    /** jti でセッションを取得する。 */
    Optional<UserSession> findByJti(String jti);

    /** jti を指定してセッションを無効化する（idempotent）。 */
    void revokeByJti(String jti);

    /**
     * 指定 jti のセッションが明示的に無効化されているかを返す。
     *
     * <p>セッションレコードが存在し かつ {@code revoked = TRUE} の場合のみ true。
     * 不在の場合は false（直接発行されたテスト用トークン等は失効扱いしない）。</p>
     */
    boolean isRevoked(String jti);
}
