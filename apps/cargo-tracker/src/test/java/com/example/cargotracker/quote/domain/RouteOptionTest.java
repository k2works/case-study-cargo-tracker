package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RouteOption 値オブジェクト")
class RouteOptionTest {

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidRouteOption() {
        RouteOption option = new RouteOption(
                List.of("SGSIN", "HKHKG"),
                21,
                new BigDecimal("120000"),
                "V-2025-001"
        );

        assertThat(option.viaLocodes()).containsExactly("SGSIN", "HKHKG");
        assertThat(option.transitDays()).isEqualTo(21);
        assertThat(option.estimatedPrice()).isEqualByComparingTo("120000");
        assertThat(option.voyageNumber()).isEqualTo("V-2025-001");
    }

    @Test
    @DisplayName("viaLocodes が空リストでも生成できる（直行便）")
    void createWithEmptyViaLocodes() {
        RouteOption option = new RouteOption(
                List.of(),
                15,
                new BigDecimal("100000"),
                "V-2025-002"
        );

        assertThat(option.viaLocodes()).isEmpty();
    }

    @Test
    @DisplayName("viaLocodes が null の場合は例外を投げる")
    void rejectNullViaLocodes() {
        assertThatThrownBy(() -> new RouteOption(
                null, 21, new BigDecimal("120000"), "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("経由港");
    }

    @Test
    @DisplayName("transitDays が 0 の場合は例外を投げる")
    void rejectZeroTransitDays() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 0, new BigDecimal("120000"), "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("所要日数");
    }

    @Test
    @DisplayName("transitDays が負の場合は例外を投げる")
    void rejectNegativeTransitDays() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), -1, new BigDecimal("120000"), "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("所要日数");
    }

    @Test
    @DisplayName("estimatedPrice が null の場合は例外を投げる")
    void rejectNullEstimatedPrice() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 21, null, "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("estimatedPrice が 0 の場合は例外を投げる")
    void rejectZeroEstimatedPrice() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 21, BigDecimal.ZERO, "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("estimatedPrice が負の場合は例外を投げる")
    void rejectNegativeEstimatedPrice() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 21, new BigDecimal("-100"), "V-2025-001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("voyageNumber が null の場合は例外を投げる")
    void rejectNullVoyageNumber() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 21, new BigDecimal("120000"), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("voyageNumber が空文字の場合は例外を投げる")
    void rejectBlankVoyageNumber() {
        assertThatThrownBy(() -> new RouteOption(
                List.of(), 21, new BigDecimal("120000"), "  "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("equals と hashCode が正しく動作する")
    void equalsAndHashCode() {
        RouteOption a = new RouteOption(List.of("SGSIN"), 21, new BigDecimal("120000"), "V-2025-001");
        RouteOption b = new RouteOption(List.of("SGSIN"), 21, new BigDecimal("120000"), "V-2025-001");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
