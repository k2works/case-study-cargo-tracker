package com.example.authms.domain;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    void save(User user);
    void updateFailedAttempts(String username, int failedAttempts);
    void lockUser(String username);
    void unlockUser(String username);
}
