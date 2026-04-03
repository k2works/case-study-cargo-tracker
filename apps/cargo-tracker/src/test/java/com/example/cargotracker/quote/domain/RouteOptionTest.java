package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RouteOption 値オブジェクト")
class RouteOptionTest {

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidRouteOption() {
        RouteOption option = createRouteOption(
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
        RouteOption option = createRouteOption(
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
        BigDecimal price = new BigDecimal("120000");
        assertThatThrownBy(() -> createRouteOption(null, 21, price, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("経由港");
    }

    @Test
    @DisplayName("transitDays が 0 の場合は例外を投げる")
    void rejectZeroTransitDays() {
        List<String> emptyVia = List.of();
        BigDecimal price = new BigDecimal("120000");
        assertThatThrownBy(() -> createRouteOption(emptyVia, 0, price, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("所要日数");
    }

    @Test
    @DisplayName("transitDays が負の場合は例外を投げる")
    void rejectNegativeTransitDays() {
        List<String> emptyVia = List.of();
        BigDecimal price = new BigDecimal("120000");
        assertThatThrownBy(() -> createRouteOption(emptyVia, -1, price, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("所要日数");
    }

    @Test
    @DisplayName("estimatedPrice が null の場合は例外を投げる")
    void rejectNullEstimatedPrice() {
        List<String> emptyVia = List.of();
        assertThatThrownBy(() -> createRouteOption(emptyVia, 21, null, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("estimatedPrice が 0 の場合は例外を投げる")
    void rejectZeroEstimatedPrice() {
        List<String> emptyVia = List.of();
        assertThatThrownBy(() -> createRouteOption(emptyVia, 21, BigDecimal.ZERO, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("estimatedPrice が負の場合は例外を投げる")
    void rejectNegativeEstimatedPrice() {
        List<String> emptyVia = List.of();
        BigDecimal negativePrice = new BigDecimal("-100");
        assertThatThrownBy(() -> createRouteOption(emptyVia, 21, negativePrice, "V-2025-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("概算料金");
    }

    @Test
    @DisplayName("voyageNumber が null の場合は例外を投げる")
    void rejectNullVoyageNumber() {
        List<String> emptyVia = List.of();
        BigDecimal price = new BigDecimal("120000");
        assertThatThrownBy(() -> createRouteOption(emptyVia, 21, price, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("voyageNumber が空文字の場合は例外を投げる")
    void rejectBlankVoyageNumber() {
        List<String> emptyVia = List.of();
        BigDecimal price = new BigDecimal("120000");
        assertThatThrownBy(() -> createRouteOption(emptyVia, 21, price, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("equals と hashCode が正しく動作する")
    void equalsAndHashCode() {
        RouteOption a = createRouteOption(List.of("SGSIN"), 21, new BigDecimal("120000"), "V-2025-001");
        RouteOption b = createRouteOption(List.of("SGSIN"), 21, new BigDecimal("120000"), "V-2025-001");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日以内なら間に合う")
    void isOnTime_間に合う() {
        RouteOption option = createRouteOption(List.of(), 10, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11);

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isTrue();
    }

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日と同日なら間に合う")
    void isOnTime_ちょうど同日() {
        RouteOption option = createRouteOption(List.of(), 9, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 10);

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isTrue();
    }

    @Test
    @DisplayName("基準日 + 所要日数 が希望着日を超えるなら間に合わない")
    void isOnTime_超過する() {
        RouteOption option = createRouteOption(List.of(), 14, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11);

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isFalse();
    }

    @Test
    @DisplayName("境界値: 所要日数 = 希望着日ちょうどの翌日なら間に合わない（+1 境界）")
    void isOnTime_境界プラス1_間に合わない() {
        // transitDays=11, Nov1+11=Nov12 > Nov11 → false
        RouteOption option = createRouteOption(List.of(), 11, new BigDecimal("100000"), "V-001");
        LocalDate baseDate = LocalDate.of(2025, 11, 1);
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 11);

        assertThat(option.isOnTime(baseDate, requestedArrivalDate)).isFalse();
    }

    @Test
    @DisplayName("Clock を注入してテスト可能な isOnTime(Clock, LocalDate) で間に合う")
    void isOnTime_Clock注入_間に合う() {
        // Clock.fixed で現在日を 2025-11-01 に固定
        Clock fixedClock = Clock.fixed(
                Instant.parse("2025-11-01T00:00:00Z"), ZoneOffset.UTC);
        RouteOption option = createRouteOption(List.of(), 10, new BigDecimal("100000"), "V-001");
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 15);

        assertThat(option.isOnTime(fixedClock, requestedArrivalDate)).isTrue();
    }

    @Test
    @DisplayName("Clock を注入してテスト可能な isOnTime(Clock, LocalDate) で間に合わない")
    void isOnTime_Clock注入_間に合わない() {
        // Clock.fixed で現在日を 2025-11-01 に固定、transitDays=20 → Nov21 > Nov15
        Clock fixedClock = Clock.fixed(
                Instant.parse("2025-11-01T00:00:00Z"), ZoneOffset.UTC);
        RouteOption option = createRouteOption(List.of(), 20, new BigDecimal("100000"), "V-001");
        LocalDate requestedArrivalDate = LocalDate.of(2025, 11, 15);

        assertThat(option.isOnTime(fixedClock, requestedArrivalDate)).isFalse();
    }

    private RouteOption createRouteOption(
            List<String> viaLocodes,
            int transitDays,
            BigDecimal estimatedPrice,
            String voyageNumber
    ) {
        return new RouteOption(viaLocodes, transitDays, estimatedPrice, voyageNumber);
    }
}
