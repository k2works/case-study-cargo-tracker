package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * シードデータのパスワードハッシュが期待どおりかを確認する。
 *
 * <p>ハッシュは目視で正しさを判断できない。<strong>貼り付けた値を信用せず、
 * 実際に照合できることをテストで固定する。</strong>
 */
class SeedPasswordTest {

    /** {@code V3__seed_users.sql} に埋め込んだハッシュ。 */
    private static final String SEED_HASH = "$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe";

    @Test
    void シードのハッシュはpasswordと一致する() {
        assertThat(new BCryptPasswordEncoder(12).matches("password", SEED_HASH)).isTrue();
    }
}
