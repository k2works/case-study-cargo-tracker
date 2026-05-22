package com.example.authms.infrastructure.persistence;

import com.example.authms.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface UserMapper {
    Optional<User> findByUsername(@Param("username") String username);
    void insert(User user);
    void updateFailedAttempts(@Param("username") String username, @Param("failedAttempts") int failedAttempts);
    void lockUser(@Param("username") String username, @Param("lockedAt") LocalDateTime lockedAt);
    void unlockUser(@Param("username") String username);
}
