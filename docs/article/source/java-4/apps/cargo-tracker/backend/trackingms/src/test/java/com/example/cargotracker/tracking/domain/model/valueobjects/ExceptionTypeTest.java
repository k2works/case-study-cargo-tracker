package com.example.cargotracker.tracking.domain.model.valueobjects;

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
 * 例外種別（domain-model.md の要素表・不変条件 7）。
 *
 * <p><b>緊急かどうかは種別が答える。</b> 属性に持たせると、起票した人が
 * 「急ぎではない紛失」を作れてしまう。</p>
 *
 * <p><b>正典を読んで突き合わせる。</b> 書き写した表は、正典が変わっても追随しない。</p>
 */
class ExceptionTypeTest {

    private static final Path CANON =
            Path.of("..", "..", "..", "..", "docs", "design", "cargo-tracker", "domain-model.md");

    @Test
    @DisplayName("5 値がそろっている")
    void hasFiveValues() {
        assertThat(ExceptionType.values())
                .containsExactly(ExceptionType.DELAY, ExceptionType.DAMAGE,
                        ExceptionType.LOSS, ExceptionType.MISROUTE,
                        ExceptionType.CUSTOMS_HOLD);
    }

    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    @DisplayName("呼び名は要素表が正典（利用者に列挙名を見せない）")
    void usesTheCanonicalLabel(ExceptionType type) throws IOException {
        assertThat(type.label()).isEqualTo(canonLabels("例外種別 `ExceptionType`").get(type.name()));
    }

    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    @DisplayName("不変条件 7: 緊急なのは紛失だけ（種別が答える）")
    void onlyLossIsUrgent(ExceptionType type) {
        assertThat(type.urgent()).isEqualTo(type == ExceptionType.LOSS);
    }

    /** 要素表の「... | 遅延 / 破損 / ... | `DELAY` / `DAMAGE` / ...」を読む。 */
    static Map<String, String> canonLabels(String rowPrefix) throws IOException {
        String source = Files.readString(CANON);
        int start = source.indexOf("| " + rowPrefix + " |");
        assertThat(start).as("要素表に %s の行が無い", rowPrefix).isGreaterThan(-1);
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
