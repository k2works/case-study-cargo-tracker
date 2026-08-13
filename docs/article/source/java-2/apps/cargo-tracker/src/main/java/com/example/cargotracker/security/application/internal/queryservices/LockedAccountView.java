package com.example.cargotracker.security.application.internal.queryservices;

import java.time.Instant;

/**
 * ロック中のアカウント（US33）。
 *
 * @param username       利用者名
 * @param failedAttempts 連続失敗回数
 * @param lockedUntil    ロックの自動解除時刻
 */
public record LockedAccountView(String username, int failedAttempts, Instant lockedUntil) {
}
