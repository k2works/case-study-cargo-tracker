package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RouteSearchQuery")
class RouteSearchQueryTest {

    @Test
    @DisplayName("有効な値で RouteSearchQuery を生成できる")
    void 有効な値でRouteSearchQueryを生成できる() {
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2025, 12, 31),
            CargoType.GENERAL, new BigDecimal("100.0")
        );

        assertThat(query.originLocode()).isEqualTo("JPTYO");
        assertThat(query.destinationLocode()).isEqualTo("USNYC");
        assertThat(query.requestedArrivalDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(query.cargoType()).isEqualTo(CargoType.GENERAL);
        assertThat(query.weightKg()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("危険物・冷凍など全 CargoType を指定できる")
    void 全CargoTypeを指定できる() {
        assertThat(new RouteSearchQuery("JPTYO", "USNYC", LocalDate.now().plusDays(30),
            CargoType.HAZARDOUS, BigDecimal.TEN).cargoType()).isEqualTo(CargoType.HAZARDOUS);

        assertThat(new RouteSearchQuery("JPTYO", "USNYC", LocalDate.now().plusDays(30),
            CargoType.REFRIGERATED, BigDecimal.TEN).cargoType()).isEqualTo(CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("出発地 LOCODE が null の場合は例外をスローする")
    void 出発地LOCODEがnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            null, "USNYC", LocalDate.now().plusDays(30),
            CargoType.GENERAL, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("出発地 LOCODE が空文字の場合は例外をスローする")
    void 出発地LOCODEが空文字の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "  ", "USNYC", LocalDate.now().plusDays(30),
            CargoType.GENERAL, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("目的地 LOCODE が null の場合は例外をスローする")
    void 目的地LOCODEがnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", null, LocalDate.now().plusDays(30),
            CargoType.GENERAL, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("目的地 LOCODE が空文字の場合は例外をスローする")
    void 目的地LOCODEが空文字の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "", LocalDate.now().plusDays(30),
            CargoType.GENERAL, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("希望着日が null の場合は例外をスローする")
    void 希望着日がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "USNYC", null,
            CargoType.GENERAL, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("貨物種別が null の場合は例外をスローする")
    void 貨物種別がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.now().plusDays(30),
            null, BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("重量が 0 の場合は例外をスローする")
    void 重量が0の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.now().plusDays(30),
            CargoType.GENERAL, BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("重量が負の場合は例外をスローする")
    void 重量が負の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.now().plusDays(30),
            CargoType.GENERAL, new BigDecimal("-1")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("重量が null の場合は例外をスローする")
    void 重量がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.now().plusDays(30),
            CargoType.GENERAL, null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
