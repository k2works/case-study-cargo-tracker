package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
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
 * 契約の通関状態 → 追跡から見た意味（US29 §受入基準 5）。
 *
 * <p><b>正典を読んで突き合わせる。</b> 契約に状態が増えたら、ここが赤になって
 * 気づける——本番で「起票も解決もされない」が静かに起きるより先に。</p>
 */
class CustomsHoldStatusTest {

    private static final Path CANON =
            Path.of("..", "..", "..", "..", "docs", "design", "cargo-tracker", "domain-model.md");

    @Test
    @DisplayName("正典の通関状態がすべて揃っている（増えたら赤になる）")
    void coversEveryCanonicalStatus() throws IOException {
        assertThat(CustomsHoldStatus.values()).extracting(Enum::name)
                .containsExactlyInAnyOrderElementsOf(canonLabels().keySet());
    }

    @ParameterizedTest
    @EnumSource(CustomsHoldStatus.class)
    @DisplayName("呼び名は要素表が正典（利用者に列挙名を見せない）")
    void usesTheCanonicalLabel(CustomsHoldStatus status) throws IOException {
        assertThat(status.label()).isEqualTo(canonLabels().get(status.name()));
    }

    @Test
    @DisplayName("起票するのは留置だけ、解決するのは決着したときだけ")
    void raisesOnlyWhenHeldAndResolvesWhenSettled() {
        // **値の一覧から回す。** 足した値をここで扱い忘れると、その値だけ
        // 起票も解決もされないまま静かに素通りする。
        for (CustomsHoldStatus status : CustomsHoldStatus.values()) {
            assertThat(status.raisesHold())
                    .as("%s で税関保留を起票するか", status)
                    .isEqualTo(status == CustomsHoldStatus.HELD);
            assertThat(status.resolvesHold())
                    .as("%s で税関保留を解決するか", status)
                    .isEqualTo(status == CustomsHoldStatus.CLEARED
                            || status == CustomsHoldStatus.REJECTED);
        }
    }

    @Test
    @DisplayName("起票と解決は同時に起きない（同じ状態が両方を意味することは無い）")
    void neverRaisesAndResolvesAtOnce() {
        for (CustomsHoldStatus status : CustomsHoldStatus.values()) {
            assertThat(status.raisesHold() && status.resolvesHold())
                    .as("%s が起票と解決の両方を意味している", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("知らない状態は断る（素通りさせない）")
    void refusesUnknownStatus() {
        assertThatThrownBy(() -> CustomsHoldStatus.of("SOMETHING"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> CustomsHoldStatus.of(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    /** 要素表の「通関状態 `CustomsStatus` | 審査中 / … | `PENDING` / …」を読む。 */
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
