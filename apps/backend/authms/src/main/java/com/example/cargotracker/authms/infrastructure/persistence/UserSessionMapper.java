package com.example.cargotracker.authms.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface UserSessionMapper {

    @Insert("""
            INSERT INTO user_sessions (jti, user_id, issued_at, expires_at, revoked)
            VALUES (#{jti}, #{userId}, #{issuedAt}, #{expiresAt}, #{revoked})
            """)
    void insert(UserSessionRecord record);

    @Select("SELECT jti, user_id, issued_at, expires_at, revoked FROM user_sessions WHERE jti = #{jti}")
    @Result(property = "userId", column = "user_id")
    @Result(property = "issuedAt", column = "issued_at")
    @Result(property = "expiresAt", column = "expires_at")
    Optional<UserSessionRecord> findByJti(String jti);

    @Update("UPDATE user_sessions SET revoked = TRUE WHERE jti = #{jti}")
    void revokeByJti(String jti);

    @Select("SELECT COUNT(*) > 0 FROM user_sessions WHERE jti = #{jti} AND revoked = FALSE")
    boolean existsActiveByJti(String jti);
}
