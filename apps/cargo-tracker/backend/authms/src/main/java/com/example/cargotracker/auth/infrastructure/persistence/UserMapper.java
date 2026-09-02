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

    record AuditLogRow(String username, String event, boolean succeeded, String remoteAddr,
            Instant occurredAt) {
    }
}
