package com.example.cargotracker.billing.domain;

import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FreightCalculationService ドメインサービス")
class FreightCalculationServiceTest {

    private final FreightCalculationService service = new FreightCalculationService();

    // ── 一般貨物 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("一般貨物 100kg の基本料金は 100 円")
    void calculateBaseAmount_generalCargo_100kg() {
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("100"), CargoType.GENERAL_CARGO);
        assertThat(result).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("一般貨物 0.5kg は小数点以下 HALF_UP で丸められる")
    void calculateBaseAmount_generalCargo_halfUp() {
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("0.5"), CargoType.GENERAL_CARGO);
        // 0.5 * 1.0 = 0.5 → HALF_UP → 1
        assertThat(result).isEqualByComparingTo(new BigDecimal("1"));
    }

    // ── 冷凍・冷蔵 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("冷凍・冷蔵 100kg の基本料金は 150 円")
    void calculateBaseAmount_refrigerated_100kg() {
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("100"), CargoType.REFRIGERATED);
        assertThat(result).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    @DisplayName("冷凍・冷蔵 10kg の基本料金は 15 円")
    void calculateBaseAmount_refrigerated_10kg() {
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("10"), CargoType.REFRIGERATED);
        assertThat(result).isEqualByComparingTo(new BigDecimal("15"));
    }

    // ── 危険物 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("危険物 100kg の基本料金は 200 円")
    void calculateBaseAmount_dangerousGoods_100kg() {
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("100"), CargoType.DANGEROUS_GOODS);
        assertThat(result).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    @DisplayName("危険物 0.4kg は HALF_UP で丸められる")
    void calculateBaseAmount_dangerousGoods_roundDown() {
        // 0.4 * 2.0 = 0.8 → HALF_UP → 1
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("0.4"), CargoType.DANGEROUS_GOODS);
        assertThat(result).isEqualByComparingTo(new BigDecimal("1"));
    }

    @Test
    @DisplayName("危険物 0.2kg は HALF_UP で丸められる")
    void calculateBaseAmount_dangerousGoods_roundDownSmall() {
        // 0.2 * 2.0 = 0.4 → HALF_UP → 0
        BigDecimal result = service.calculateBaseAmount(new BigDecimal("0.2"), CargoType.DANGEROUS_GOODS);
        assertThat(result).isEqualByComparingTo(new BigDecimal("0"));
    }

    // ── バリデーション ────────────────────────────────────────────────────

    @Test
    @DisplayName("重量が null の場合は IllegalArgumentException をスローする")
    void calculateBaseAmount_nullWeight_throwsException() {
        assertThatThrownBy(() -> service.calculateBaseAmount(null, CargoType.GENERAL_CARGO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("貨物種別が null の場合は IllegalArgumentException をスローする")
    void calculateBaseAmount_nullCargoType_throwsException() {
        assertThatThrownBy(() -> service.calculateBaseAmount(new BigDecimal("100"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("重量が負の場合は IllegalArgumentException をスローする")
    void calculateBaseAmount_negativeWeight_throwsException() {
        assertThatThrownBy(() -> service.calculateBaseAmount(new BigDecimal("-1"), CargoType.GENERAL_CARGO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
