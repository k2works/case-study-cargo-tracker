package com.example.authms.infrastructure.repositories;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, username, email, display_name, password, enabled, failed_attempts, locked_until
            FROM users
            WHERE username = #{username}
            """)
    @Result(column = "display_name", property = "displayName")
    @Result(column = "failed_attempts", property = "failedAttempts")
    @Result(column = "locked_until", property = "lockedUntil")
    UserRecord findByUsername(@Param("username") String username);

    /**
     * いまロックされている利用者（US32-1）。
     *
     * <p><strong>期限切れは含めない。</strong>期限が切れたロックは受け付けが戻っており、
     * 一覧に出すと管理者は要らない作業をする。判定の「いま」は呼び出し側が渡す
     * （ここで {@code NOW()} を使うと、テストと実装で別の時刻を見る）。
     */
    @Select("""
            SELECT id, username, email, display_name, password, enabled,
                   failed_attempts, locked_until
            FROM users
            WHERE locked_until IS NOT NULL AND locked_until > #{now}
            ORDER BY locked_until DESC
            """)
    @Result(column = "display_name", property = "displayName")
    @Result(column = "failed_attempts", property = "failedAttempts")
    @Result(column = "locked_until", property = "lockedUntil")
    List<UserRecord> findLocked(@Param("now") Instant now);

    @Select("SELECT role FROM user_roles WHERE user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT link.shipper_id
            FROM user_shipper_link link
            INNER JOIN users u ON u.id = link.user_id
            WHERE u.username = #{username}
            """)
    Long findLinkedShipperId(@Param("username") String username);

    @Delete("""
            DELETE FROM user_shipper_link
            WHERE user_id = (
                SELECT id FROM users WHERE username = #{username}
            )
            """)
    void deleteShipperLink(@Param("username") String username);

    @Insert("""
            INSERT INTO user_shipper_link (user_id, shipper_id)
            SELECT id, #{shipperId}
            FROM users
            WHERE username = #{username}
            """)
    void insertShipperLink(@Param("username") String username,
            @Param("shipperId") Long shipperId);

    @Update("""
            UPDATE users
            SET failed_attempts = #{failedAttempts}, locked_until = #{lockedUntil}
            WHERE id = #{id}
            """)
    void updateLoginState(
            @Param("id") Long id,
            @Param("failedAttempts") int failedAttempts,
            @Param("lockedUntil") Instant lockedUntil);

    /**
     * 読み取った時点から失敗回数が変わっていない場合にだけ書き込む。
     *
     * <p>「読んで足して書く」だけでは、同時に届いた試行が同じ回数を読んで同じ値を書き、
     * 何度失敗してもロックが成立しない。更新できた件数を返し、0 なら誰かが先に加算している。
     *
     * @return 更新した行数（0 または 1）
     */
    @Update("""
            UPDATE users
            SET failed_attempts = #{failedAttempts}, locked_until = #{lockedUntil}
            WHERE id = #{id} AND failed_attempts = #{expectedFailedAttempts}
            """)
    int updateLoginStateIfUnchanged(
            @Param("id") Long id,
            @Param("failedAttempts") int failedAttempts,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("expectedFailedAttempts") int expectedFailedAttempts);
}
