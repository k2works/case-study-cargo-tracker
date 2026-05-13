package com.example.cargotracker.authms.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO users (id, username, email, password, enabled, created_at, updated_at)
            VALUES (#{id}, #{username}, #{email}, #{password}, #{enabled}, #{createdAt}, #{updatedAt})
            """)
    void insert(UserRecord record);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            VALUES (#{userId}, (SELECT id FROM roles WHERE name = #{roleName}))
            """)
    void insertUserRole(@Param("userId") String userId, @Param("roleName") String roleName);

    @Select("SELECT id, username, email, password, enabled, created_at, updated_at FROM users WHERE id = #{id}")
    @Results({
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<UserRecord> findById(String id);

    @Select("SELECT id, username, email, password, enabled, created_at, updated_at FROM users WHERE username = #{username}")
    @Results({
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<UserRecord> findByUsername(String username);

    @Select("SELECT id, username, email, password, enabled, created_at, updated_at FROM users WHERE email = #{email}")
    @Results({
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<UserRecord> findByEmail(String email);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE username = #{username}")
    boolean existsByUsername(String username);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE email = #{email}")
    boolean existsByEmail(String email);

    @Select("SELECT r.name FROM roles r INNER JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(String userId);
}
