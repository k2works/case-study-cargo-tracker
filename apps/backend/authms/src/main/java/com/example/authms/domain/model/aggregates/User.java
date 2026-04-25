package com.example.authms.domain.model.aggregates;

import com.example.authms.domain.model.valueobjects.Email;
import com.example.authms.domain.model.valueobjects.Password;
import com.example.authms.domain.model.valueobjects.Role;
import com.example.authms.domain.model.valueobjects.UserName;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class User {
    private Long id;
    private UserName username;
    private Email email;
    private Password password;
    private boolean enabled;
    private Set<Role> roles;
    private LocalDateTime createdAt;

    protected User() {
        this.roles = new HashSet<>();
    }

    public User(UserName username, Email email, Password password) {
        this();
        this.username = username;
        this.email = email;
        this.password = password;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserName getUsername() {
        return username;
    }

    public void setUsername(UserName username) {
        this.username = username;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void setRoles(Set<Role> roles) {
        this.roles = new HashSet<>(roles);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
