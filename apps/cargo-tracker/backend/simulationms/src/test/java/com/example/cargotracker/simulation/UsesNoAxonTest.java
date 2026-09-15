package com.example.cargotracker.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 本番コードは Axon に触らない（[ADR-0020] 決定 3 / IT16 のレビュー N7）。
 *
 * <p><b>切り分けの道具を、切り分けたい相手に依存させない。</b> このサービスは
 * 集約もイベントも持たないのに、起動確認が Axon Server への接続を待っていた
 * ——Axon Server が止まっている状況こそ、シミュレーションを流して確かめたい
 * 場面である。</p>
 *
 * <p><b>「書いた保証」を赤で固定する。</b> ADR に「使わない」と書き、起動クラスの
 * コメントにも書いたが、それだけでは次に誰かが {@code @Import} を足したときに
 * 何も起きない。</p>
 */
class UsesNoAxonTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path CONFIG = Path.of("src/main/resources/application.yml");

    private static List<Path> javaSources() throws IOException {
        try (var paths = Files.walk(SOURCES)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("検査が空振りしていない（走査の対象が実在する）")
    void scansSomething() throws IOException {
        assertThat(javaSources())
                .as("**走査の対象が 0 件なら、下の検査は何も確かめていない**")
                .hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("[ADR-0020] 決定 3: 本番コードは Axon を参照しない")
    void referencesNoAxonType() throws IOException {
        List<Path> offenders = javaSources().stream()
                .filter(UsesNoAxonTest::mentionsAxon)
                .toList();

        assertThat(offenders)
                .as("Axon を参照しているファイル: %s", offenders)
                .isEmpty();
    }

    @Test
    @DisplayName("設定にも Axon を置かない（置くといつの間にか依存が戻る）")
    void configuresNoAxon() throws IOException {
        // **コメントは残す**（なぜ置かないかを次の人に伝える）ので、
        // 設定の「キー」として現れていないことを見る。
        String config = Files.readString(CONFIG, StandardCharsets.UTF_8);
        assertThat(config.lines().filter(line -> !line.trim().startsWith("#")).toList())
                .as("application.yml に Axon の設定がある")
                .noneMatch(line -> line.contains("axon"));
    }

    private static boolean mentionsAxon(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains("axonframework");
        } catch (IOException e) {
            throw new IllegalStateException("読めないファイルがある: " + path, e);
        }
    }
}
