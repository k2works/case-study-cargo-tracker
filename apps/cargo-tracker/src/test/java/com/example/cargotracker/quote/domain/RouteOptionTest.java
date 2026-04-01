package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // ── isOnTime ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日以内なら間に合う")
    void isOnTime_間に合う() {
        RouteOption option = new RouteOption(List.of(), 10, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11); // 10 日後

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isTrue();
    }

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日と同日なら間に合う")
    void isOnTime_ちょうど同日() {
        RouteOption option = new RouteOption(List.of(), 10, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11);

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isTrue();
    }

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日を超えるなら間に合わない")
    void isOnTime_超過する() {
        RouteOption option = new RouteOption(List.of(), 14, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11); // 10 日後（14 日では超過）

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isFalse();
    }
}
