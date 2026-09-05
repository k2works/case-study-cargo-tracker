package com.example.cargotracker.shared.domain.attention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 要確認一覧の識別子（IT4 の返済 R.1・R.2）。
 *
 * <p>IT2 で「同じ事実に毎回別の行が積み上がる」欠陥（{@code UUID.randomUUID()}）を
 * 直したとき、導出の実装が bookingms と routingms に 1 本ずつ書かれ、<b>区切り文字が
 * 食い違ったまま</b>になっていた（NUL とパイプ）。ここへ寄せる。</p>
 */
class AttentionItemIdTest {

    @Test
    @DisplayName("同じ事実なら同じ識別子になる")
    void isDerivedFromTheFact() {
        AttentionItemId first = AttentionItemId.of("PROJECTION_REJECTED", "SHIPPER", "S-1", "重複");
        AttentionItemId second = AttentionItemId.of("PROJECTION_REJECTED", "SHIPPER", "S-1", "重複");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("何か 1 つでも違えば別の識別子になる")
    void discriminatesEveryComponent() {
        AttentionItemId base = AttentionItemId.of("KIND", "TYPE", "ID", "REASON");

        assertThat(AttentionItemId.of("OTHER", "TYPE", "ID", "REASON")).isNotEqualTo(base);
        assertThat(AttentionItemId.of("KIND", "OTHER", "ID", "REASON")).isNotEqualTo(base);
        assertThat(AttentionItemId.of("KIND", "TYPE", "OTHER", "REASON")).isNotEqualTo(base);
        assertThat(AttentionItemId.of("KIND", "TYPE", "ID", "OTHER")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("区切りをまたいだ付け替えでは衝突しない")
    void isNotAmbiguousAcrossTheSeparator() {
        // 単純な連結（区切り無し・区切りが本文に現れうる文字）だと、
        // ("AB","C") と ("A","BC") が同じ種になり、別々の事実が 1 行に潰れる。
        assertThat(AttentionItemId.of("AB", "C", "ID", "REASON"))
                .isNotEqualTo(AttentionItemId.of("A", "BC", "ID", "REASON"));
        assertThat(AttentionItemId.of("A|B", "C", "ID", "REASON"))
                .isNotEqualTo(AttentionItemId.of("A", "B|C", "ID", "REASON"));
    }

    @Test
    @DisplayName("UUID の見た目に整形しない")
    void doesNotDisguiseItselfAsUuid() {
        // IT2 の実装はハイフンを入れて UUID に見せていた。導出値であることが
        // 読めなくなり、「採番された値」と誤解した変更を招く（R.1）。
        String value = AttentionItemId.of("KIND", "TYPE", "ID", "REASON").value();

        assertThat(value).doesNotContain("-").matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("attention_item.item_id（VARCHAR(36)）に収まる")
    void fitsTheColumn() {
        assertThat(AttentionItemId.of("KIND", "TYPE", "ID", "REASON").value().length())
                .isLessThanOrEqualTo(36);
    }

    @Test
    @DisplayName("導出でない値を識別子として受け取らない")
    void rejectsValueThatIsNotADigest() {
        // 採番した値・UUID の見た目に整形した値を持ち込ませない。持ち込めると、
        // 同じ事実に別の識別子が付いた行が並ぶ（IT2 で実在した欠陥に戻る）。
        assertThatThrownBy(() -> new AttentionItemId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttentionItemId("9f86d081884c7d65-9a2feaa0c55ad015"))
                .as("ハイフン入りは UUID の見た目に整形した値")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttentionItemId("9F86D081884C7D659A2FEAA0C55AD015"))
                .as("大文字は導出と食い違う（同じ事実が 2 行になる）")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttentionItemId("9f86d081"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("欠けた要素を受け取らない")
    void rejectsMissingComponents() {
        assertThatThrownBy(() -> AttentionItemId.of(null, "TYPE", "ID", "REASON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttentionItemId.of("KIND", "TYPE", " ", "REASON"))
                .as("空白の対象を通すと、別々の事実が 1 行に潰れる")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
