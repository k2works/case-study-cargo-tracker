package com.example.bookingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("危険物クラス（国連分類）")
class HazardClassTest {

    @Test
    @DisplayName("一覧にある分類は受け入れる")
    void acceptsListedClass() {
        assertThat(HazardClass.of("3")).isEqualTo(HazardClass.CLASS_3);
        assertThat(HazardClass.CLASS_3.label()).isEqualTo("引火性液体");
    }

    /**
     * 一覧に無い値を通さない。
     *
     * <p>自由入力だと「Class 3」「3類」「引火性液体」が同じ意味で混ざり、
     * どの航海が運べるか・どこに置くかを分類で判断できなくなる。
     */
    @Test
    @DisplayName("一覧に無い値は新規に受け入れない")
    void rejectsUnlistedClass() {
        assertThatThrownBy(() -> HazardClass.of("Class 3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("一覧から選んで");
        assertThatThrownBy(() -> HazardClass.of("引火性液体"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HazardClass.of("10"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 復元では検査しない。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。自由入力だった頃の
     * 予約が開けなくなるのは、分類の統一とは別の被害である。
     */
    @Test
    @DisplayName("自由入力だった頃の値も、行が開ける形で読める")
    void restoresLegacyValueAsUnknown() {
        assertThat(HazardClass.restore("Class 3")).isEqualTo(HazardClass.UNKNOWN);
        assertThat(HazardClass.restore("3")).isEqualTo(HazardClass.CLASS_3);
        assertThat(HazardClass.restore(null)).isEqualTo(HazardClass.UNKNOWN);
    }

    @Test
    @DisplayName("分類不明は選択肢に出さない（次の編集で正しい分類に直る）")
    void doesNotOfferUnknownAsChoice() {
        assertThat(HazardClass.selectableList()).doesNotContain(HazardClass.UNKNOWN).hasSize(9);
    }
}
