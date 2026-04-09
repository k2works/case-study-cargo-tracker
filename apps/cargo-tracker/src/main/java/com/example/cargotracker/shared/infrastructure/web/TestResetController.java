package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2E テスト用 DB リセットエンドポイント。
 * test プロファイルかつ app.test-reset.enabled=true の場合のみ有効化される。
 * POST /api/test/reset で cargo・shipper テーブルをリセットする。
 */
@Profile("test")
@ConditionalOnProperty(prefix = "app.test-reset", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/api/test")
class TestResetController {

    private final JdbcTemplate jdbcTemplate;

    TestResetController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset() {
        jdbcTemplate.execute("TRUNCATE TABLE cargo RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE shipper RESTART IDENTITY CASCADE");
        return ResponseEntity.noContent().build();
    }
}
