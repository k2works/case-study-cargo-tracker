package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * シードデータのパスワードハッシュが期待どおりかを確認する。
 *
 * <p>ハッシュは目視で正しさを判断できない。<strong>貼り付けた値を信用せず、
 * 実際に照合できることをテストで固定する。</strong>
 *
 * <p>ハッシュは SQL から読み取る。テストに書き写すと、SQL 側を差し替えても
 * テストは緑のまま通り、固定できているのは「この定数がハッシュであること」だけになる。
 * 写さず引用する。
 */
class SeedPasswordTest {

    private static final String SEED_SQL = "db/seed/V800__seed_users.sql";

    private static final Pattern HASH = Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[^']+)'");

    @Test
    void シードのハッシュはpasswordと一致する() throws IOException {
        Set<String> hashes = hashesInSeed();

        assertThat(hashes).as("%s からハッシュを読み取れること", SEED_SQL).isNotEmpty();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        assertThat(hashes).allSatisfy(hash ->
                assertThat(encoder.matches("password", hash))
                        .as("シードのハッシュ %s が password と一致すること", hash)
                        .isTrue());
    }

    private static Set<String> hashesInSeed() throws IOException {
        try (InputStream in =
                SeedPasswordTest.class.getClassLoader().getResourceAsStream(SEED_SQL)) {
            assertThat(in).as("クラスパス上に %s があること", SEED_SQL).isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> found = new LinkedHashSet<>();
            Matcher matcher = HASH.matcher(sql);
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            return found;
        }
    }
}
