package com.example.cargotracker.acceptance.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 受け入れが実際に回っている数を固定する（IT16 の教訓）。
 *
 * <p><b>書いたが未定義のステップは何も検査しない。</b> feature があっても
 * ステップ定義が無ければ、シナリオは「未定義」として飛ばされ、スイートは
 * 緑のまま終わる——<b>シナリオ数を数で書く</b>ことでしか気づけない。</p>
 *
 * <p><b>数は減らす向きにも守る。</b> 消したシナリオに誰も気づかないと、
 * 確かめていたはずのものが静かに消える。</p>
 */
class SimulationFeatureCountTest {

    private static final Path FEATURE =
            Path.of("src/simulationTest/resources/features/業務シミュレーション.feature");

    /**
     * 回っているシナリオの数。
     *
     * <p>内訳: 正常系・失敗・二重実行・待ち・本番の断り・工程の読み・一覧が 9 件
     * （IT16）、例外シナリオの例が 3 件・誤配・キャンセルが 2 件、継続実行が 3 件
     * （IT17）。<b>足したらここも直す</b>——直し忘れたら赤になる。</p>
     */
    private static final int EXPECTED_SCENARIOS = 17;

    @Test
    @DisplayName("受け入れのシナリオ数が変わったら気づく（黙って減らさない）")
    void countsTheScenarios() throws IOException {
        List<String> lines = Files.readAllLines(FEATURE, StandardCharsets.UTF_8);
        long outlines = lines.stream()
                .filter(line -> line.startsWith("  シナリオテンプレート:")).count();
        long plain = lines.stream().filter(line -> line.startsWith("  シナリオ:")).count();
        // テンプレートは「例」の表の**見出し行を除いた**行数だけ回る。
        // 見出しを数えると、例を 1 つも書いていないテンプレートが 1 件として
        // 数えられる——**回っていないものを数に入れない**。
        long tableRows = lines.stream().filter(line -> line.trim().startsWith("|")).count();
        long examples = tableRows - outlines;

        assertThat(plain + examples)
                .as("シナリオ %d 件 + テンプレート %d 件の例 %d 件", plain, outlines, examples)
                .isEqualTo(EXPECTED_SCENARIOS);
    }
}
