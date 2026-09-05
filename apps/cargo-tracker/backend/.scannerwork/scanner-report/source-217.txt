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
 * 呼ばれないこと」を統合テストで固定すると約束している。</p>
 *
 * <p><b>{@code ReplayIT} は IT2 で先に書いた</b>（Reaction Handler より前）。投影が
 * 3 つに増え、投影が {@code attention_item} への書き込みという副作用を持つに至った
 * ためである。書いた時点で実在の欠陥が出た（読み直しのたびに要確認一覧が増えていた）。</p>
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
    @DisplayName("ADR-0001 決定 4: ReplayIT が実在する")
    void replayCheckExists() throws IOException {
        // Reaction Handler の有無に関わらず要る。投影が副作用（attention_item への
        // 書き込み）を持つ以上、リプレイで何が起きるかは常に検査の対象である。
        assertThat(replayChecks())
                .as("ADR-0001 のコンプライアンス欄が約束している検査が無い")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Reaction Handler を入れたら、コマンドの再送を見る検査も入れる")
    void replayCheckCoversCommandsOnceReactionHandlersExist() throws IOException {
        List<Path> handlers = reactionHandlers();
        if (handlers.isEmpty()) {
            return;
        }

        // Reaction Handler が現れたら、ReplayIT が「リプレイ中にコマンドが送られない」
        // ことまで見ていなければならない。行が増えないことだけでは足りない。
        boolean checksCommands = false;
        for (Path check : replayChecks()) {
            String body = Files.readString(check, StandardCharsets.UTF_8);
            if (body.contains("CommandGateway")) {
                checksCommands = true;
                break;
            }
        }

        assertThat(checksCommands)
                .as("Reaction Handler があるのに、ReplayIT がコマンドの再送を見ていない"
                        + "（ADR-0001 決定 4）。対象: %s", handlers)
                .isTrue();
    }

    @Test
    @DisplayName("ADR-0001 の記述が、実際の状態と一致している")
    void theAdrSaysWhetherTheCheckExists() throws IOException {
        String adr = Files.readString(
                backendRoot().getParent().getParent().getParent()
                        .resolve("docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md"),
                StandardCharsets.UTF_8);

        assertThat(adr.contains("**IT1 時点では未実装**"))
                .as("ReplayIT を書いたのに ADR に『未実装』が残っている。"
                        + "検査を書いたら ADR の記述も同じ変更で直す")
                .isFalse();
    }
}
