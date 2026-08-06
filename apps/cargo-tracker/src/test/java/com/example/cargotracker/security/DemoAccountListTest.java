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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ログイン画面に一覧する動作確認用アカウントと、シードされる利用者の一致を検証する。
 *
 * <p><strong>一覧とシードがずれると、画面の説明が嘘になる。</strong> 存在しない ID を
 * 案内すれば「ログインできない」と報告され、シードにあるのに一覧に無い利用者は
 * 誰にも使われない。どちらも気づきにくく、気づいたときには原因の切り分けに時間がかかる。
 *
 * <p>設定ファイルとマイグレーションという別々の場所に同じ事実が書かれている以上、
 * 一致は人のレビューではなくテストで固定する。
 */
class DemoAccountListTest {

    private static final Pattern SEED_USERNAME =
            Pattern.compile("^\\s*\\('([a-z]+)',", Pattern.MULTILINE);

    private static final Pattern CONFIGURED_USERNAME =
            Pattern.compile("^\\s*- username:\\s*(\\S+)", Pattern.MULTILINE);

    @ParameterizedTest
    @ValueSource(strings = {"application-local.yml", "application-dev.yml"})
    void 一覧する利用者はシードされた利用者と一致する(String profileConfig) throws IOException {
        Set<String> seeded = extract(SEED_USERNAME, read("db/seed/V800__seed_users.sql"));
        Set<String> configured = extract(CONFIGURED_USERNAME, read(profileConfig));

        assertThat(seeded)
                .as("V800__seed_users.sql から利用者を読み取れていること（正規表現の前提が崩れていない）")
                .isNotEmpty();
        assertThat(configured)
                .as("%s の app.demo-login.accounts", profileConfig)
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(seeded);
    }

    @Test
    void 事前入力する利用者は一覧に含まれる() throws IOException {
        // 事前入力した ID が一覧に無いと、「この ID は何者か」が画面から辿れない
        String config = read("application-local.yml");
        Matcher m = Pattern.compile("demo-login:\\s*\\n\\s+enabled:.*\\n\\s+username:\\s*(\\S+)")
                .matcher(config);
        assertThat(m.find()).as("app.demo-login.username を読み取れること").isTrue();
        assertThat(extract(CONFIGURED_USERNAME, config)).contains(m.group(1));
    }

    private static Set<String> extract(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String read(String classpathResource) throws IOException {
        try (InputStream in = DemoAccountListTest.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            assertThat(in).as("クラスパス上に %s があること", classpathResource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
