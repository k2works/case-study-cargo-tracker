package com.example.cargotracker.authms.infrastructure.persistence;

import java.time.LocalDateTime;

public class UserRecord {
    public String id;
    public String username;
    public String email;
    public String password;
    public boolean enabled;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
