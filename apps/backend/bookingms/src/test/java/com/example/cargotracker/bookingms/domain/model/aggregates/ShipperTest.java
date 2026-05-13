package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Shipper 集約テスト")
class ShipperTest {

    @Test
    @DisplayName("個人荷主を作成できる")
    void 個人荷主を作成できる() {
        Shipper shipper = Shipper.createIndividual("田中太郎", "tanaka@example.com", "090-1234-5678");

        assertThat(shipper.getName()).isEqualTo("田中太郎");
        assertThat(shipper.getEmail()).isEqualTo("tanaka@example.com");
        assertThat(shipper.getPhone()).isEqualTo("090-1234-5678");
        assertThat(shipper.getShipperType()).isEqualTo(ShipperType.INDIVIDUAL);
        assertThat(shipper.getShipperCode()).startsWith("SHP-");
        assertThat(shipper.getContractNumber()).isNull();
        assertThat(shipper.getDiscountRate()).isNull();
    }

    @Test
    @DisplayName("法人荷主を作成できる")
    void 法人荷主を作成できる() {
        Shipper shipper = Shipper.createCorporate(
                "株式会社テスト", "corp@example.com", "03-1234-5678",
                "CNT-001", new BigDecimal("0.10"));

        assertThat(shipper.getName()).isEqualTo("株式会社テスト");
        assertThat(shipper.getEmail()).isEqualTo("corp@example.com");
        assertThat(shipper.getShipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(shipper.getContractNumber()).isEqualTo("CNT-001");
        assertThat(shipper.getDiscountRate()).isEqualTo(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("割引率が範囲外（0.30 超）の場合例外")
    void 割引率が範囲外の場合例外() {
        BigDecimal overRate = new BigDecimal("0.31");
        assertThatThrownBy(() -> Shipper.createCorporate(
                "株式会社テスト", "corp@example.com", "03-1234-5678", "CNT-001", overRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("割引率");
    }

    @Test
    @DisplayName("割引率が負の場合例外")
    void 割引率が負の場合例外() {
        BigDecimal negativeRate = new BigDecimal("-0.01");
        assertThatThrownBy(() -> Shipper.createCorporate(
                "株式会社テスト", "corp@example.com", "03-1234-5678", "CNT-001", negativeRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("割引率");
    }

    @Test
    @DisplayName("name が null の場合例外")
    void nameがnullの場合例外() {
        assertThatThrownBy(() -> Shipper.createIndividual(null, "test@example.com", "090-0000-0000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
