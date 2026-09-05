package com.example.cargotracker.routing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 探索の打ち切りの上限は ADR-0007 が正典である。
 *
 * <p>ADR-0007 の決定 5 は「上限は業務の判断なので、変えるときはこの ADR を改める。
 * 定数だけを書き換えない」と定めている。<b>決定を検査に落とさなければ守られない。</b>
 * 定数だけを直しても ADR は赤くならないので、ここで突き合わせる。</p>
 */
class RouteSearchCutoffCanonTest {

    private static Path adr() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("docs/adr/cargo-tracker"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("docs/adr/cargo-tracker が見つかりません");
        }
        return dir.resolve("docs/adr/cargo-tracker/0007-route-search-cutoff.md");
    }

    private static String adrText() throws IOException {
        return Files.readString(adr(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("ADR-0007 が定める上限と、実装の定数が一致する")
    void constantsMatchTheAdr() throws IOException {
        String text = adrText();

        assertThat(text)
                .as("ADR に乗り継ぎの上限が書かれていない（検査が空振りしている）")
                .contains("`MAX_TRANSFERS = " + RouteSearchService.MAX_TRANSFERS + "`");
        assertThat(text)
                .as("ADR に候補数の上限が書かれていない（検査が空振りしている）")
                .contains("`MAX_CANDIDATES = " + RouteSearchService.MAX_CANDIDATES + "`");
    }

    @Test
    @DisplayName("ADR-0007 は「件数は探索の打ち切りではなく表示の上限」と書いている")
    void adrRecordsThatTheLimitAppliesAfterOrdering() throws IOException {
        // 当初は探索の途中で切っており、返るのが「推奨順の上位」でなく
        // 「先に見つかった順」になっていた（IT5 レビューで訂正）。
        // 実装を戻す変更をしたら、この文と食い違うことに気づけるようにする。
        assertThat(adrText())
                .as("決定 2 の訂正が ADR から消えている。実装と食い違う")
                .contains("並べたあとの表示の上限");
    }
}
