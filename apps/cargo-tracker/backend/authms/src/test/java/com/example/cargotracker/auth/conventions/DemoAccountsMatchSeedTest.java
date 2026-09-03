package com.example.cargotracker.auth.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 画面に出す動作確認用の利用者と、実際に入る利用者を一致させる（ADR-0004）。
 *
 * <p>一覧に載っているのに入っていない利用者は、選んだ人に「利用者名または
 * パスワードが正しくありません」を返す。認証の失敗は理由を区別しない仕様なので、
 * 「そもそも居ない」ことは画面からは決して分からない。だから検査で固定する。</p>
 */
class DemoAccountsMatchSeedTest {

    /** 利用者 ID を含む行だけでなく、対象になりうる行を全部拾ってから形を見る。 */
    private static final Pattern SEED_USER = Pattern.compile("^\\s*\\('([a-z0-9]+)',", Pattern.MULTILINE);

    private static final Pattern LIST_USER = Pattern.compile("username:\\s*'([a-z0-9]+)'");

    private static Path repositoryFile(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(relative + " が見つかりません");
    }

    private static Set<String> namesIn(Path file, Pattern pattern) throws IOException {
        String content = Files.readString(file);
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    @Test
    @DisplayName("画面の一覧とシードの利用者は同じ顔ぶれである")
    void listAndSeedHoldTheSameUsers() throws IOException {
        Path seed = repositoryFile(
                "apps/cargo-tracker/backend/authms/src/main/resources/db/seed/R__demo_users.sql");
        Path list = repositoryFile(
                "apps/cargo-tracker/frontend/src/features/auth/demoAccounts.ts");

        Set<String> seeded = namesIn(seed, SEED_USER);
        Set<String> listed = namesIn(list, LIST_USER);

        // 読めていないと検査は空振りする。0 件でないことを先に確かめる。
        assertThat(seeded).as("シードの SQL から利用者を拾えていない").isNotEmpty();
        assertThat(listed).as("画面の一覧から利用者を拾えていない").isNotEmpty();

        assertThat(listed)
                .as("画面に出るのに入っていない利用者は、選んでもログインできない")
                .containsExactlyInAnyOrderElementsOf(seeded);
    }

    @Test
    @DisplayName("ログインできない利用者がシードにも居る")
    void seedContainsAnAccountThatCannotSignIn() throws IOException {
        Path seed = repositoryFile(
                "apps/cargo-tracker/backend/authms/src/main/resources/db/seed/R__demo_users.sql");

        // 一覧に「ログインできない利用者」を載せる以上、実際に落ちなければ確認の役に立たない。
        assertThat(Files.readString(seed))
                .as("enabled = FALSE の利用者が入っていない")
                .containsPattern("'disabled01',[^\\n]*FALSE");
    }
}
