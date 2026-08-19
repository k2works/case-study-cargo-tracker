package com.example.authms.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, username, email, display_name, password, enabled, failed_attempts, locked_until
            FROM users
            WHERE username = #{username}
            """)
    @Results({
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "failed_attempts", property = "failedAttempts"),
        @Result(column = "locked_until", property = "lockedUntil")
    })
    UserRecord findByUsername(@Param("username") String username);

    @Select("SELECT role FROM user_roles WHERE user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE users
            SET failed_attempts = #{failedAttempts}, locked_until = #{lockedUntil}
            WHERE id = #{id}
            """)
    void updateLoginState(
            @Param("id") Long id,
            @Param("failedAttempts") int failedAttempts,
            @Param("lockedUntil") Instant lockedUntil);
}
