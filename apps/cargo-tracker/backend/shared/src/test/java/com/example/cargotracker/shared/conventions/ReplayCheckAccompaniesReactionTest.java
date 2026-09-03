package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reaction Handler を入れるなら、同じ変更で {@code ReplayIT} も入れる（ADR-0001 決定 4）。
 *
 * <p>ADR のコンプライアンス欄は「リプレイ中に {@code CommandGateway} が 1 度も
 * 呼ばれないこと」を統合テストで固定すると約束している。IT1 時点では Reaction
 * Handler が 1 つも無いため書けず、約束が文章のまま残っている。</p>
 *
 * <p><strong>ArchUnit の規則では代われない。</strong>{@code CommandGateway} の
 * 利用箇所をパッケージで限定する規則はコンパイル時の依存しか見ておらず、
 * 「リプレイの実行中に呼ばれない」という動的な保証は別物である。</p>
 *
 * <p>この検査は、最初の Reaction Handler が入った日に赤くなる。約束を思い出す
 * 手段を人の記憶に置かないためである。</p>
 */
class ReplayCheckAccompaniesReactionTest {

    private static Path backendRoot() {
        return Path.of("").toAbsolutePath().getParent();
    }

    /**
     * 業務サービスの {@code application/reaction} にある実装（package-info を除く）。
     *
     * <p>テストのフィクスチャ（{@code archfixture}）は数えない。規則が赤を出せることを
     * 確かめるための偽物であり、リプレイで動く本物の Reaction Handler ではない。</p>
     */
    private static List<Path> reactionHandlers() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/application/reaction/"))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .toList();
        }
    }

    private static List<Path> replayChecks() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> p.getFileName().toString().endsWith("ReplayIT.java"))
                    .toList();
        }
    }

    @Test
    @DisplayName("ADR-0001 決定 4: Reaction Handler があるなら ReplayIT もある")
    void replayCheckExistsOnceReactionHandlersDo() throws IOException {
        List<Path> handlers = reactionHandlers();
        if (handlers.isEmpty()) {
            // まだ 1 つも無い＝ ADR に「未実装」と書いてある状態と一致する。
            assertThat(replayChecks())
                    .as("Reaction Handler が無いのに ReplayIT がある。"
                            + "ADR-0001 のコンプライアンス欄から『IT1 時点では未実装』を消すこと")
                    .isEmpty();
            return;
        }

        assertThat(replayChecks())
                .as("Reaction Handler を入れたら ReplayIT も入れる（ADR-0001 決定 4）。"
                        + "対象: %s", handlers)
                .isNotEmpty();
    }

    @Test
    @DisplayName("ADR-0001 の未実装の記述が、実際の状態と一致している")
    void theAdrSaysWhetherTheCheckExists() throws IOException {
        String adr = Files.readString(
                backendRoot().getParent().getParent().getParent()
                        .resolve("docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md"),
                StandardCharsets.UTF_8);

        boolean adrSaysUnimplemented = adr.contains("**IT1 時点では未実装**");
        assertThat(adrSaysUnimplemented)
                .as("ReplayIT の有無（%s）と ADR の記述が食い違っている。"
                        + "検査を書いたら ADR の『未実装』も同じ変更で消す", replayChecks())
                .isEqualTo(replayChecks().isEmpty());
    }
}
