package com.example.cargotracker.handling.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 通関状態（domain-model.md の要素表・US29 §受入基準 3）。
 *
 * <p><b>判定は列挙が答える。</b> 呼び出し側に {@code if (status == CLEARED)} を書かせると、
 * 引取のガードが 2 経路（その場の記録・預かりからの再適用）に分かれたときに、
 * 片方だけ直る形になる。</p>
 *
 * <p><b>正典を読んで突き合わせる。</b> 書き写した表は、正典が変わっても追随しない。</p>
 */
class CustomsStatusTest {

    /** 呼び名の正典（domain-model.md の要素表）。 */
    private static final Path CANON =
            Path.of("..", "..", "..", "..", "docs", "design", "cargo-tracker", "domain-model.md");

    @Test
    @DisplayName("4 値がそろっている")
    void hasFourValues() {
        assertThat(CustomsStatus.values())
                .containsExactly(CustomsStatus.PENDING, CustomsStatus.CLEARED,
                        CustomsStatus.HELD, CustomsStatus.REJECTED);
    }

    @ParameterizedTest
    @EnumSource(CustomsStatus.class)
    @DisplayName("呼び名は要素表が正典（利用者に列挙名を見せない）")
    void usesTheCanonicalLabel(CustomsStatus status) throws IOException {
        assertThat(status.label()).isEqualTo(canonLabels().get(status.name()));
    }

    @Test
    @DisplayName("引取を許すのは通関済だけ")
    void onlyClearedAllowsClaim() {
        // **値の一覧から回す**（IT11 の「列挙に値を足したら全箇所を回る」）。
        // 足した値をここで扱い忘れると、その値だけ引取が素通りする。
        for (CustomsStatus status : CustomsStatus.values()) {
            assertThat(status.allowsClaim())
                    .as("%s で引取を許すか", status)
                    .isEqualTo(status == CustomsStatus.CLEARED);
        }
    }

    @Test
    @DisplayName("未決着は審査中と留置（決着したものは出し直せない／出し直せる）")
    void pendingAndHeldAreUnsettled() {
        // 不変条件 3「未決着の申告は貨物あたり高々 1 件」がこの述語を読む。
        // 判定をアプリケーション層に書き写すと、片方だけ直せる場所が増える。
        for (CustomsStatus status : CustomsStatus.values()) {
            assertThat(status.unsettled())
                    .as("%s は未決着か", status)
                    .isEqualTo(status == CustomsStatus.PENDING || status == CustomsStatus.HELD);
        }
    }

    /** 要素表の「通関状態 `CustomsStatus` | 審査中 / 通関済 / 留置 / 不可 | `PENDING` / ...」を読む。 */
    private static Map<String, String> canonLabels() throws IOException {
        String source = Files.readString(CANON);
        int start = source.indexOf("| 通関状態 `CustomsStatus` |");
        assertThat(start).as("要素表に通関状態の行が無い").isGreaterThan(-1);
        String[] cells = source.substring(start, source.indexOf('\n', start)).split("\\|");
        String[] labels = cells[2].trim().split("\\s*/\\s*");
        String[] names = cells[3].trim().replace("`", "").split("\\s*/\\s*");
        assertThat(labels).hasSameSizeAs(names);

        Map<String, String> canon = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            canon.put(names[i], labels[i]);
        }
        return canon;
    }
}
