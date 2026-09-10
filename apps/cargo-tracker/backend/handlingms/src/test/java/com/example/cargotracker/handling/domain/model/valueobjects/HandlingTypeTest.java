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
 * 荷役種別ごとの要件（domain-model.md「荷役種別ごとの要件」・要素表）。
 *
 * <p><b>要件は型自身が持つ。</b> 呼び出し側に {@code if (type == LOAD)} を書かせると、
 * 種別が増えたときに書き換える場所が散らばる。</p>
 *
 * <p><b>正典を読んで突き合わせる。</b> 書き写した表は、正典が変わっても追随しない
 * （`navigationMatchesUiDesign.test.ts` の先例）。</p>
 */
class HandlingTypeTest {

    /** 呼び名の正典（domain-model.md の要素表）。 */
    private static final Path CANON =
            Path.of("..", "..", "..", "..", "docs", "design", "cargo-tracker", "domain-model.md");

    @Test
    @DisplayName("4 値がそろっている（税関は荷役ではなく CustomsDeclaration で扱う）")
    void hasFourValues() {
        assertThat(HandlingType.values())
                .containsExactly(HandlingType.RECEIVE, HandlingType.LOAD,
                        HandlingType.UNLOAD, HandlingType.CLAIM);
    }

    @ParameterizedTest
    @EnumSource(HandlingType.class)
    @DisplayName("呼び名は要素表が正典（利用者に列挙名を見せない）")
    void usesTheCanonicalLabel(HandlingType type) throws IOException {
        assertThat(type.label()).isEqualTo(canonLabels().get(type.name()));
    }

    /** 要素表の「荷役種別 `HandlingType` | 受領 / 積込 / 荷降し / 引取 | `RECEIVE` / ...」を読む。 */
    private static Map<String, String> canonLabels() throws IOException {
        String source = Files.readString(CANON);
        int start = source.indexOf("| 荷役種別 `HandlingType` |");
        assertThat(start).as("要素表に荷役種別の行が無い").isGreaterThan(-1);
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

    @Test
    @DisplayName("要件表: 航海番号が要るのは積込と荷降し（船に紐づく作業）")
    void requiresVoyageNumberForLoadAndUnload() {
        assertThat(HandlingType.RECEIVE.requiresVoyageNumber()).isFalse();
        assertThat(HandlingType.LOAD.requiresVoyageNumber()).isTrue();
        assertThat(HandlingType.UNLOAD.requiresVoyageNumber()).isTrue();
        assertThat(HandlingType.CLAIM.requiresVoyageNumber()).isFalse();
    }

    @Test
    @DisplayName("要件表: 荷受人の確認が要るのは引取だけ")
    void requiresConsigneeConfirmationForClaimOnly() {
        assertThat(HandlingType.RECEIVE.requiresConsigneeConfirmation()).isFalse();
        assertThat(HandlingType.LOAD.requiresConsigneeConfirmation()).isFalse();
        assertThat(HandlingType.UNLOAD.requiresConsigneeConfirmation()).isFalse();
        assertThat(HandlingType.CLAIM.requiresConsigneeConfirmation()).isTrue();
    }

    @Test
    @DisplayName("要件表: 通関の検査が要るのは引取だけ（警告でなく拒否）")
    void requiresCustomsClearanceForClaimOnly() {
        // **列挙に値を足したら全箇所を回る**（IT12 レビュー 中）。2 値だけを見ると、
        // `UNLOAD` や `RECEIVE` に誤って真を返す実装に変えても緑になる。
        for (HandlingType type : HandlingType.values()) {
            assertThat(type.requiresCustomsClearance())
                    .as("%s で通関を要求するか", type)
                    .isEqualTo(type == HandlingType.CLAIM);
        }
    }
}
