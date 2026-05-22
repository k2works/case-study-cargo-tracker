package com.example.authms.infrastructure.persistence;

import com.example.authms.domain.User;
import com.example.authms.domain.UserRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public void save(User user) {
        userMapper.insert(user);
    }

    @Override
    public void updateFailedAttempts(String username, int failedAttempts) {
        userMapper.updateFailedAttempts(username, failedAttempts);
    }

    @Override
    public void lockUser(String username) {
        userMapper.lockUser(username, LocalDateTime.now());
    }

    @Override
    public void unlockUser(String username) {
        userMapper.unlockUser(username);
    }
}
