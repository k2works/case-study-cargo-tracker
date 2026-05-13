package com.example.cargotracker.authms.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    String SELECT_COLUMNS = "id, username, email, password, enabled, created_at, updated_at, failed_attempts, lock_until";

    @Insert("""
            INSERT INTO users (id, username, email, password, enabled, created_at, updated_at,
                               failed_attempts, lock_until)
            VALUES (#{id}, #{username}, #{email}, #{password}, #{enabled}, #{createdAt}, #{updatedAt},
                    #{failedAttempts}, #{lockUntil})
            """)
    void insert(UserRecord userRow);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            VALUES (#{userId}, (SELECT id FROM roles WHERE name = #{roleName}))
            """)
    void insertUserRole(@Param("userId") String userId, @Param("roleName") String roleName);

    @Select("SELECT " + SELECT_COLUMNS + " FROM users WHERE id = #{id}")
    @Result(property = "createdAt", column = "created_at")
    @Result(property = "updatedAt", column = "updated_at")
    @Result(property = "failedAttempts", column = "failed_attempts")
    @Result(property = "lockUntil", column = "lock_until")
    Optional<UserRecord> findById(String id);

    @Select("SELECT " + SELECT_COLUMNS + " FROM users WHERE username = #{username}")
    @Result(property = "createdAt", column = "created_at")
    @Result(property = "updatedAt", column = "updated_at")
    @Result(property = "failedAttempts", column = "failed_attempts")
    @Result(property = "lockUntil", column = "lock_until")
    Optional<UserRecord> findByUsername(String username);

    @Select("SELECT " + SELECT_COLUMNS + " FROM users WHERE email = #{email}")
    @Result(property = "createdAt", column = "created_at")
    @Result(property = "updatedAt", column = "updated_at")
    @Result(property = "failedAttempts", column = "failed_attempts")
    @Result(property = "lockUntil", column = "lock_until")
    Optional<UserRecord> findByEmail(String email);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE username = #{username}")
    boolean existsByUsername(String username);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE email = #{email}")
    boolean existsByEmail(String email);

    @Select("SELECT r.name FROM roles r INNER JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(String userId);

    @Select("SELECT " + SELECT_COLUMNS + " FROM users")
    @Result(property = "createdAt", column = "created_at")
    @Result(property = "updatedAt", column = "updated_at")
    @Result(property = "failedAttempts", column = "failed_attempts")
    @Result(property = "lockUntil", column = "lock_until")
    List<UserRecord> findAll();

    @Update("""
            UPDATE users
            SET enabled = #{enabled},
                updated_at = #{updatedAt},
                failed_attempts = #{failedAttempts},
                lock_until = #{lockUntil}
            WHERE id = #{id}
            """)
    void update(UserRecord userRow);

    @Delete("DELETE FROM user_roles WHERE user_id = #{userId}")
    void deleteRolesByUserId(String userId);
}
