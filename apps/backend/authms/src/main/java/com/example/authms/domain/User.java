package com.example.authms.domain;

import java.time.LocalDateTime;

public class User {

    private Long id;
    private String username;
    private String password;
    private Role role;
    private int failedLoginAttempts;
    private LocalDateTime lockedAt;

    public User() {}

    public User(Long id, String username, String password, Role role,
                int failedLoginAttempts, LocalDateTime lockedAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedAt = lockedAt;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedAt = LocalDateTime.now();
        }
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedAt = null;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockedAt() { return lockedAt; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(Role role) { this.role = role; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
}
