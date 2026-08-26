package com.example.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 貨物種別（[ADR-027] 決定 1）。
 *
 * <p><strong>列挙に値を足したら、それを使う場所すべてを回る</strong>（IT10 Try 3）。
 * 係数を決め忘れた値があると、その貨物だけ料金が出ない——あるいは黙って 1.0 が
 * 掛かって、危険物が一般貨物と同じ運賃になる。
 */
@DisplayName("貨物種別")
class CargoTypeTest {

    /**
     * <strong>値を足したら、この検査が赤になる。</strong>
     *
     * <p>赤を見た人は「その貨物の係数はいくつか」を決めることになる。
     */
    @Test
    @DisplayName("貨物種別は、bookingms と同じ 3 値である")
    void hasTheAgreedValues() {
        assertThat(Arrays.stream(CargoType.values()).map(Enum::name))
                .as("貨物種別が増減した。**係数を決めること**——決め忘れると、"
                        + "その貨物だけ料金が出ないか、黙って一般貨物と同じ運賃になる")
                .containsExactly("GENERAL", "HAZARDOUS", "REFRIGERATED");
    }

    /**
     * <strong>すべての値が表示名を持つ</strong>。
     *
     * <p>表示名を `ChargeBasisResponse` の名簿で持つと、載せ忘れた値が
     * <strong>そのまま英字で画面に出る</strong>——名簿方式は載っていないものを
     * 通すため、載せ忘れたものほど漏れる。列挙が自分で持てば、足した瞬間に
     * コンパイルが止まる。
     */
    @ParameterizedTest
    @EnumSource(CargoType.class)
    @DisplayName("すべての貨物種別が日本語の表示名を持つ")
    void everyValueHasALabel(CargoType type) {
        assertThat(type.label())
                .as("表示名を決めていない貨物種別がある: %s", type)
                .isNotBlank()
                .isNotEqualTo(type.name());
    }

    /**
     * <strong>すべての値が係数を持つ</strong>（Try 3 の一般形）。
     *
     * <p>名簿を書き写さず、実体（{@code values()}）から回す。
     */
    @ParameterizedTest
    @EnumSource(CargoType.class)
    @DisplayName("すべての貨物種別が、正の係数を持つ")
    void everyTypeHasAPositiveFactor(CargoType type) {
        assertThat(type.factor())
                .as("%s の係数が決まっていない。運賃が 0 円になる", type)
                .isPositive();
    }

    /**
     * <strong>知らない種別は断る</strong>（ACL 用の変換）。
     *
     * <p>既定値（一般貨物）に倒すと、bookingms が新しい種別を足したときに
     * <strong>その貨物だけ安く請求される</strong>——名簿方式の検査は
     * 「載っていないもの」を通すと、載せ忘れたものほど漏れる。
     */
    @Test
    @DisplayName("扱いを決めていない貨物種別は断る")
    void rejectsUnknownCargoType() {
        assertThatThrownBy(() -> CargoType.of("LIVESTOCK"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扱いを決めていない");
        assertThatThrownBy(() -> CargoType.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        // **大文字小文字の揺れも断る。** 通すと、送り側の表記ゆれを黙って吸収し、
        // 契約が崩れていることに気づけない
        assertThatThrownBy(() -> CargoType.of("general"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 知っている種別は引ける。 */
    @ParameterizedTest
    @EnumSource(CargoType.class)
    @DisplayName("すべての貨物種別を、名前から引ける")
    void resolvesEveryTypeByName(CargoType type) {
        assertThat(CargoType.of(type.name())).isSameAs(type);
    }

    /**
     * <strong>危険物と冷凍は一般貨物より高い。</strong>
     *
     * <p>すべて 1.0 にしても「正の係数を持つ」検査は通る。**区別されていること**を見る。
     */
    @Test
    @DisplayName("危険物と冷凍・冷蔵は、一般貨物より高い係数を持つ")
    void chargesMoreForSpecialCargo() {
        assertThat(CargoType.HAZARDOUS.factor())
                .as("危険物が一般貨物と同じ運賃になっている")
                .isGreaterThan(CargoType.GENERAL.factor());
        assertThat(CargoType.REFRIGERATED.factor())
                .as("冷凍・冷蔵が一般貨物と同じ運賃になっている")
                .isGreaterThan(CargoType.GENERAL.factor());
    }
}
