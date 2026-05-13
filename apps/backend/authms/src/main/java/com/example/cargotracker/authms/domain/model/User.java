package com.example.cargotracker.authms.domain.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private final UserId id;
    private UserName username;
    private Email email;
    private PasswordHash passwordHash;
    private boolean enabled;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final Set<Role> roles;

    @SuppressWarnings("java:S107")
    private User(UserId id, UserName username, Email email, PasswordHash passwordHash,
                 boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.roles = new HashSet<>(roles);
    }

    public static User create(UserName username, Email email, PasswordHash passwordHash) {
        if (username == null) throw new IllegalArgumentException("username は必須です");
        if (email == null) throw new IllegalArgumentException("email は必須です");
        if (passwordHash == null) throw new IllegalArgumentException("passwordHash は必須です");
        var now = LocalDateTime.now();
        return new User(UserId.generate(), username, email, passwordHash, true, now, now, new HashSet<>());
    }

    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt, new HashSet<>());
    }

    @SuppressWarnings("java:S107")
    public static User reconstruct(UserId id, UserName username, Email email,
                                   PasswordHash passwordHash, boolean enabled,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, Set<Role> roles) {
        return new User(id, username, email, passwordHash, enabled, createdAt, updatedAt, roles);
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void setRoles(Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
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
