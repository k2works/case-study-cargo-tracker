package com.example.billingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 地点の地域区分（[ADR-027] 決定 1 の改訂）。
 *
 * <p><strong>距離の代わりである。</strong>区分を足したのに係数を決め忘れると、
 * その区分を通る貨物だけ運賃が出ないか、黙って国内と同じ額になる。
 */
@DisplayName("地点の地域区分")
class PortRegionTest {

    @Test
    @DisplayName("地域区分は、地点マスタと同じ 3 値である")
    void hasTheAgreedValues() {
        assertThat(Arrays.stream(PortRegion.values()).map(Enum::name))
                .as("地域区分が増減した。**係数を決めること**——決め忘れると、"
                        + "その区分を通る貨物だけ国内と同じ運賃になる")
                .containsExactly("DOMESTIC", "NEAR_SEA", "OCEAN");
    }

    /** <strong>名簿を書き写さず、実体（{@code values()}）から回す。</strong> */
    @ParameterizedTest
    @EnumSource(PortRegion.class)
    @DisplayName("すべての区分が正の係数と表示名を持つ")
    void everyRegionHasAFactorAndALabel(PortRegion region) {
        assertThat(region.factor())
                .as("係数を決めていない区分がある: %s", region)
                .isPositive();
        assertThat(region.label()).isNotBlank().isNotEqualTo(region.name());
    }

    /**
     * <strong>遠いほど高い。</strong>順序が崩れると、太平洋横断が国内より安くなる。
     */
    @Test
    @DisplayName("遠洋・近海・国内の順に係数が大きい")
    void chargesMoreForDistantRegions() {
        assertThat(PortRegion.OCEAN.factor())
                .isGreaterThan(PortRegion.NEAR_SEA.factor());
        assertThat(PortRegion.NEAR_SEA.factor())
                .isGreaterThan(PortRegion.DOMESTIC.factor());
    }

    /**
     * <strong>知らない区分は断る。</strong>既定値（国内）に倒すと、
     * 地点マスタに新しい区分を足したときに、その港を通る貨物だけ安く請求される。
     */
    @Test
    @DisplayName("扱いを決めていない区分は断る")
    void rejectsUnknownRegions() {
        assertThatThrownBy(() -> PortRegion.of("MOON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortRegion.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
