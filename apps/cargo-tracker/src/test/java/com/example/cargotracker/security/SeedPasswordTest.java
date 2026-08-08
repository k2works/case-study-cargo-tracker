package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.SqlResources;
import java.io.IOException;
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
 *
 * <p><strong>{@code db/seed} 配下のすべてを読む</strong>（IT6 / IT5 の Try T3）。
 * かつては {@code V800} だけを読んでいた。後から足したシードのハッシュが
 * 検査されないまま通り、<strong>そのアカウントだけログインできない</strong>状態を
 * 見逃す。1 ファイルを名指しする検査は、ファイルが増えた瞬間に効かなくなる。
 */
class SeedPasswordTest {

    private static final String SEED_DIRECTORY = "db/seed";

    private static final Pattern HASH = Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[^']+)'");

    @Test
    void シードのハッシュはpasswordと一致する() throws IOException {
        Set<String> hashes = hashesInSeed();

        assertThat(hashes).as("%s の全ファイルからハッシュを読み取れること", SEED_DIRECTORY)
                .isNotEmpty();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        assertThat(hashes).allSatisfy(hash ->
                assertThat(encoder.matches("password", hash))
                        .as("シードのハッシュ %s が password と一致すること", hash)
                        .isTrue());
    }

    private static Set<String> hashesInSeed() throws IOException {
        String sql = SqlResources.readAll(SEED_DIRECTORY);
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = HASH.matcher(sql);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
