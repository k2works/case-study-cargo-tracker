package com.example.cargotracker.authms.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class User {

    private final UserId id;
    private UserName username;
    private Email email;
    private PasswordHash passwordHash;
    private boolean enabled;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private User(UserId id, UserName username, Email email, PasswordHash passwordHash,
                 boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(UserName username, Email email, PasswordHash passwordHash) {
        if (username == null) throw new IllegalArgumentException("username は必須です");
        if (email == null) throw new IllegalArgumentException("email は必須です");
        if (passwordHash == null) throw new IllegalArgumentException("passwordHash は必須です");
        var now = LocalDateTime.now();
        return new User(UserId.generate(), username, email, passwordHash, true, now, now);
    }

    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt);
    }

    public UserId id() { return id; }
    public UserName username() { return username; }
    public Email email() { return email; }
    public PasswordHash passwordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
