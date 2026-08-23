package com.example.handlingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 荷役の種別ごとの要件（[ADR-023] 決定 1）。
 *
 * <p>要件を種別そのものに持たせるのは、呼び出し側に {@code if (type == LOAD)} を書かせない
 * ためである。書かせると、種別が増えたときに書き換える場所が散らばり、<strong>書き換え忘れた
 * 場所だけが黙って通す</strong>。
 */
@DisplayName("荷役の種別")
class HandlingTypeTest {

    @Nested
    @DisplayName("デシジョンテーブル（ADR-023 決定 1）")
    class DecisionTable {

        /** 積込・荷降しに航海番号が要るのは、どの船に載せたか分からないと貨物を追えないため。 */
        @Test
        @DisplayName("航海番号が要るのは積込と荷降しだけ")
        void requiresVoyageNumberOnlyForLoadAndUnload() {
            assertThat(HandlingType.RECEIVE.requiresVoyageNumber()).isFalse();
            assertThat(HandlingType.LOAD.requiresVoyageNumber()).isTrue();
            assertThat(HandlingType.UNLOAD.requiresVoyageNumber()).isTrue();
            assertThat(HandlingType.CLAIM.requiresVoyageNumber()).isFalse();
        }

        /**
         * 荷受人の確認が要るのは引取だけ（[ADR-023] 決定 4）。
         *
         * <p>通関ガード（{@code CustomsDeclaration}・US29）の<strong>代替</strong>である。
         * ガードが無いまま引取を通すと「通関前の貨物を引き渡した」記録が残る。
         */
        @Test
        @DisplayName("荷受人の確認が要るのは引取だけ")
        void requiresConsigneeConfirmationOnlyForClaim() {
            assertThat(HandlingType.RECEIVE.requiresConsigneeConfirmation()).isFalse();
            assertThat(HandlingType.LOAD.requiresConsigneeConfirmation()).isFalse();
            assertThat(HandlingType.UNLOAD.requiresConsigneeConfirmation()).isFalse();
            assertThat(HandlingType.CLAIM.requiresConsigneeConfirmation()).isTrue();
        }

        @Test
        @DisplayName("照合する港は種別ごとに違う")
        void checksDifferentPortPerType() {
            assertThat(HandlingType.RECEIVE.expectedPort()).isEqualTo(ExpectedPort.ORIGIN);
            assertThat(HandlingType.LOAD.expectedPort()).isEqualTo(ExpectedPort.ITINERARY_LOAD);
            assertThat(HandlingType.UNLOAD.expectedPort()).isEqualTo(ExpectedPort.ITINERARY_UNLOAD);
            assertThat(HandlingType.CLAIM.expectedPort()).isEqualTo(ExpectedPort.DESTINATION);
        }
    }

    /**
     * <strong>種別を 1 つ足したら赤になる。</strong>
     *
     * <p>上の 3 つは値を名指しで並べているため、種別が増えても緑のままである。
     * 網羅を列挙から導いて、要件を決め忘れた種別を落とす。
     */
    @ParameterizedTest
    @EnumSource(HandlingType.class)
    @DisplayName("すべての種別が、照合する港を決めている")
    void everyTypeDecidesItsExpectedPort(HandlingType type) {
        assertThat(type.expectedPort())
                .as("%s の照合する港が決まっていない。ADR-023 決定 1 の表に足すこと", type)
                .isNotNull();
    }

    /**
     * <strong>種別の読み方は 1 か所に置く。</strong>
     *
     * <p>入口とユースケースの 2 か所で {@code valueOf} を呼んでいると、入口が増えた日に
     * 種別の不正が別の見え方をする——片方は業務のメッセージで断り、もう片方は素の例外で
     * 500 になる。
     */
    @ParameterizedTest
    @EnumSource(HandlingType.class)
    @DisplayName("語彙にある名前は読める")
    void parsesEveryKnownName(HandlingType type) {
        assertThat(HandlingType.parse(type.name())).isEqualTo(type);
    }

    @Test
    @DisplayName("語彙に無い名前と空は、業務のメッセージで断る")
    void rejectsUnknownAndBlankNames() {
        assertThatThrownBy(() -> HandlingType.parse("CUSTOMS_INSPECTION"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷役の種別が不正です");
        assertThatThrownBy(() -> HandlingType.parse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷役の種別を選んでください");
        assertThatThrownBy(() -> HandlingType.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷役の種別を選んでください");
    }

    @Test
    @DisplayName("種別は 4 つ。増減したら ADR-023 決定 1 の表を直す")
    void hasExactlyFourTypes() {
        assertThat(EnumSet.allOf(HandlingType.class))
                .containsExactly(HandlingType.RECEIVE, HandlingType.LOAD,
                        HandlingType.UNLOAD, HandlingType.CLAIM);
    }
}
