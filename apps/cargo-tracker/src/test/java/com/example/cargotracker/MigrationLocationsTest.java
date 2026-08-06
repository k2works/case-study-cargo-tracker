package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Flyway の適用範囲（{@code spring.flyway.locations}）を検証する。
 *
 * <p><strong>動作確認用の利用者と業務データが本番に適用されてはならない。</strong>
 * かつて利用者シードは {@code db/migration/common} にあり、ファイル冒頭のコメントで
 * 「本番環境ではこのマイグレーションを適用しない」と宣言していた。しかし common は
 * すべてのプロファイルの locations に含まれるため、その宣言は何も強制していなかった。
 * パスワードがリポジトリに公開されているアカウントが本番 DB に作られる構成だった。
 *
 * <p>宣言ではなく配置で守り、配置が正しいことをここで固定する。
 * 設定ファイルは実行しないと効果が見えないため、目視のレビューでは同じ見落としが再発する。
 */
class MigrationLocationsTest {

    private static final Pattern LOCATIONS =
            Pattern.compile("^\\s*locations:\\s*(.+)$", Pattern.MULTILINE);

    @ParameterizedTest
    @ValueSource(strings = {"db/seed", "db/demo"})
    void 本番既定のlocationsに動作確認用データは含まれない(String excluded) throws IOException {
        assertThat(locationsOf("application.yml")).doesNotContain(excluded);
    }

    @Test
    void 本番既定のlocationsは業務スキーマのみである() throws IOException {
        assertThat(locationsOf("application.yml"))
                .isEqualTo("classpath:db/migration/common,classpath:db/migration/{vendor}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application-local.yml", "application-dev.yml"})
    void 開発環境は利用者シードと業務データの両方を適用する(String config) throws IOException {
        assertThat(locationsOf(config)).contains("db/seed").contains("db/demo");
    }

    @Test
    void テストは利用者シードのみ適用し業務データは適用しない() throws IOException {
        // 業務データを入れると「本番に混ざらないこと」を検証する DemoDataTest が
        // 意味を失う。認証に必要な利用者だけを入れる
        assertThat(locationsOf("application-test.yml"))
                .contains("db/seed")
                .doesNotContain("db/demo");
    }

    private static String locationsOf(String classpathResource) throws IOException {
        try (InputStream in = MigrationLocationsTest.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            assertThat(in).as("クラスパス上に %s があること", classpathResource).isNotNull();
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = LOCATIONS.matcher(yaml);
            assertThat(matcher.find())
                    .as("%s に spring.flyway.locations があること", classpathResource)
                    .isTrue();
            return matcher.group(1).trim();
        }
    }
}
