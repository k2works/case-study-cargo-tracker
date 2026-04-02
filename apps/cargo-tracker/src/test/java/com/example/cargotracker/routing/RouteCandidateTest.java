package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RouteCandidate")
class RouteCandidateTest {

    @Test
    @DisplayName("有効な値で RouteCandidate を生成できる")
    void 有効な値でRouteCandidateを生成できる() {
        var candidate = new RouteCandidate(
            "VOY001", List.of("SGSIN", "HKHKG"), 14,
            new BigDecimal("1500.00"), LocalDate.of(2025, 12, 31),
            Set.of(CargoType.GENERAL, CargoType.REFRIGERATED)
        );

        assertThat(candidate.voyageNumber()).isEqualTo("VOY001");
        assertThat(candidate.viaLocodes()).containsExactly("SGSIN", "HKHKG");
        assertThat(candidate.transitDays()).isEqualTo(14);
        assertThat(candidate.estimatedPrice()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(candidate.estimatedArrival()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(candidate.supportedCargoTypes()).containsExactlyInAnyOrder(CargoType.GENERAL, CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("経由港なしで RouteCandidate を生成できる")
    void 経由港なしでRouteCandidateを生成できる() {
        var candidate = new RouteCandidate(
            "VOY002", List.of(), 7,
            BigDecimal.TEN, LocalDate.now().plusDays(7),
            Set.of(CargoType.GENERAL)
        );

        assertThat(candidate.viaLocodes()).isEmpty();
    }

    @Test
    @DisplayName("航海番号が null の場合は例外をスローする")
    void 航海番号がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            null, List.of(), 7, BigDecimal.TEN, LocalDate.now().plusDays(7),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("航海番号が空文字の場合は例外をスローする")
    void 航海番号が空文字の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "  ", List.of(), 7, BigDecimal.TEN, LocalDate.now().plusDays(7),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("経由港リストが null の場合は例外をスローする")
    void 経由港リストがnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", null, 7, BigDecimal.TEN, LocalDate.now().plusDays(7),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("所要日数が 0 の場合は例外をスローする")
    void 所要日数が0の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 0, BigDecimal.TEN, LocalDate.now().plusDays(1),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("所要日数が負の場合は例外をスローする")
    void 所要日数が負の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), -1, BigDecimal.TEN, LocalDate.now().plusDays(1),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("概算料金が 0 の場合は例外をスローする")
    void 概算料金が0の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, BigDecimal.ZERO, LocalDate.now().plusDays(10),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("概算料金が負の場合は例外をスローする")
    void 概算料金が負の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, new BigDecimal("-100"), LocalDate.now().plusDays(10),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("概算料金が null の場合は例外をスローする")
    void 概算料金がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, null, LocalDate.now().plusDays(10),
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("推定到着日が null の場合は例外をスローする")
    void 推定到着日がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, BigDecimal.TEN, null,
            Set.of(CargoType.GENERAL)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("対応貨物種別が null の場合は例外をスローする")
    void 対応貨物種別がnullの場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, BigDecimal.TEN, LocalDate.now().plusDays(10),
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("対応貨物種別が空の場合は例外をスローする")
    void 対応貨物種別が空の場合は例外をスローする() {
        assertThatThrownBy(() -> new RouteCandidate(
            "VOY001", List.of(), 10, BigDecimal.TEN, LocalDate.now().plusDays(10),
            Set.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
