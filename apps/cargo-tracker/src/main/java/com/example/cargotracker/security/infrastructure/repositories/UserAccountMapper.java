package com.example.cargotracker.security.infrastructure.repositories;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 認証アカウントの MyBatis マッパー。 */
@Mapper
public interface UserAccountMapper {

    @Select("""
            SELECT id, username, email, password, enabled, failed_attempts, locked_until,
                   shipper_id AS shipperId
              FROM users
             WHERE username = #{username}
            """)
    @Results(id = "userAccount", value = {
        @Result(column = "failed_attempts", property = "failedAttempts"),
        @Result(column = "locked_until", property = "lockedUntil")
    })
    UserAccountRecord findByUsername(@Param("username") String username);

    /** 失敗回数の更新用に行を排他ロックして取得する（同一利用者への並行更新を直列化する）。 */
    @Select("""
            SELECT id, username, email, password, enabled, failed_attempts, locked_until,
                   shipper_id AS shipperId
              FROM users
             WHERE username = #{username}
               FOR UPDATE
            """)
    @ResultMap("userAccount")
    UserAccountRecord findByUsernameForUpdate(@Param("username") String username);

    @Select("SELECT role FROM user_roles WHERE user_id = #{userId}")
    List<String> findRoles(@Param("userId") Long userId);

    @Update("""
            UPDATE users
               SET failed_attempts = #{failedAttempts},
                   locked_until    = #{lockedUntil},
                   updated_at      = CURRENT_TIMESTAMP
             WHERE id = #{id}
            """)
    int updateLockState(
            @Param("id") Long id,
            @Param("failedAttempts") int failedAttempts,
            @Param("lockedUntil") Instant lockedUntil);
}
