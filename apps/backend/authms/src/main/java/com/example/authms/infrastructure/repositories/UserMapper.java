package com.example.authms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    UserRecord selectByUsername(@Param("username") String username);
    UserRecord selectByEmail(@Param("email") String email);
    UserRecord selectById(@Param("id") Long id);
    void insertUser(UserRecord record);
    List<RoleRecord> selectRolesByUserId(@Param("userId") Long userId);
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
    RoleRecord selectRoleByName(@Param("name") String name);
}
