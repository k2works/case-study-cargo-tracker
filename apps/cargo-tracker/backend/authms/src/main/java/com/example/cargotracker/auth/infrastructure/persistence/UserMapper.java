package com.example.cargotracker.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    UserRow findByUsername(@Param("username") String username);

    @Select("SELECT role FROM user_roles WHERE username = #{username}")
    List<String> findRoles(@Param("username") String username);

    @Update("""
            UPDATE users SET failed_attempts = #{failedAttempts}, locked_until = #{lockedUntil},
                             updated_at = #{updatedAt}
             WHERE username = #{username}
            """)
    int updateSignInState(@Param("username") String username,
            @Param("failedAttempts") int failedAttempts,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("updatedAt") Instant updatedAt);

    int insertAuditLog(AuditLogRow row);

    record UserRow(
            String username,
            String passwordHash,
            String displayName,
            String shipperId,
            boolean enabled,
            int failedAttempts,
            Instant lockedUntil) {
    }

    /**
     * 認証の試行・ロック・解除の記録（US31 §受入基準 7）。
     *
     * <p>{@code eventType} は {@code LOGIN_SUCCESS} / {@code LOGIN_FAILURE} /
     * {@code LOCKED} / {@code UNLOCKED}。{@code reason} は断った理由
     * （{@code BAD_CREDENTIALS} / {@code LOCKED} / {@code DISABLED}）で、
     * <b>画面には出さない</b>。利用者に返すメッセージは同一にする一方、
     * 記録では区別できなければ、総当たりと打ち間違いを見分けられない。</p>
     */
    record AuditLogRow(String username, String eventType, String reason, String remoteAddr,
            Instant occurredAt) {
    }
}
