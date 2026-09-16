package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR は「決定」と同じ数だけ「検査」を持つ。
 *
 * <p><b>文章のまま残った決定は守られない。</b> ADR-0009 は「規則を書いただけで
 * 検査に落とさなかったため、7 IT のあいだ半分しか守られず違反が 5 本増えた」
 * （IT7 の教訓）。ADR-0010 以降は「検査」の節を必ず置いている。</p>
 *
 * <p><b>ここでは節の有無だけを見る。</b> 中身が正しいかは読む人が判断する——
 * 自動で確かめられるのは「対応させたかどうか」までで、それでも
 * <b>「対応させ忘れた」を赤にできる</b>。</p>
 *
 * <p><b>古い ADR を今すぐ全部直すことは求めない。</b> 直したものから外していく
 * ための名簿を持ち、<b>名簿に載っていない新しい ADR は必ず検査を持つ</b>。
 * 名簿は減る方向にしか動かさない（増やすときは、なぜ検査を書けないかを
 * ここに書く）。</p>
 */
class AdrHasChecksTest {

    private static final Path ADR_DIR = Path.of("..", "..", "..", "..", "docs", "adr", "cargo-tracker");

    /**
     * 検査の節をまだ持たない ADR。<b>減らす方向にしか動かさない</b>。
     *
     * <p>IT9 で ADR-0008 を外した（決定 4 つは実装済みだったが、決定 3 の
     * 「ラベルの無い要素は例外にする」と決定 4 の読み口に検査が無かった）。</p>
     */
    private static final List<String> WITHOUT_CHECKS_YET = List.of(
            "0001-cqrs-es-with-axon-in-microservices.md",
            "0002-event-store-axon-server-and-postgresql-read-models.md",
            "0003-crypto-shredding-for-personal-data.md",
            "0004-demo-login-for-development.md",
            "0005-flyway-locations-per-service.md",
            "0006-role-authorization-at-the-gateway.md",
            "0007-route-search-cutoff.md",
            "0009-condition-review-is-not-a-state-transition.md");

    @Test
    @DisplayName("新しい ADR は「検査」の節を持つ（決定を文章のまま残さない）")
    void everyNewAdrDeclaresItsChecks() throws IOException {
        List<String> missing = adrs()
                .filter(adr -> !WITHOUT_CHECKS_YET.contains(adr.getFileName().toString()))
                .filter(AdrHasChecksTest::lacksChecks)
                .map(adr -> adr.getFileName().toString())
                .toList();

        assertThat(missing)
                .as("検査に落とさなかった決定は守られない（ADR-0009 は 7 IT のあいだ半分だけ守られた）")
                .isEmpty();
    }

    @Test
    @DisplayName("名簿は素通しにならない（載っている ADR が実在する）")
    void theListOnlyNamesRealAdrs() throws IOException {
        List<String> names = adrs().map(adr -> adr.getFileName().toString()).toList();

        assertThat(names)
                .as("名簿に無い ADR を書いている。ファイル名を変えたら名簿も直す")
                .containsAll(WITHOUT_CHECKS_YET);
    }

    /**
     * 「検査」の節を持たないか。
     *
     * <p><b>見出しは行として一致させる。</b> 前方一致で見ると
     * {@code ## 検査だったもの} のような見出しまで通り、節を消しても赤にならない
     * （IT9 で実測——最初はこの形で書いてしまった）。</p>
     */
    private static boolean lacksChecks(Path adr) {
        try {
            return Files.readAllLines(adr).stream().noneMatch("## 検査"::equals);
        } catch (IOException e) {
            throw new IllegalStateException("読めませんでした: " + adr, e);
        }
    }

    private static Stream<Path> adrs() throws IOException {
        return Files.list(ADR_DIR)
                .filter(path -> path.getFileName().toString().matches("\\d{4}-.*\\.md"))
                .sorted();
    }
}
