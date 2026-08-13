package com.example.cargotracker.security.infrastructure.repositories;

import java.time.Instant;

/** ロック中アカウントの行。 */
public class LockedAccountRow {

    private String username;
    private int failedAttempts;
    private Instant lockedUntil;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
