package com.example.cargotracker.shipper.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("法人荷主（US03）")
class CorporateShipperTest {

    @Test
    @DisplayName("法人荷主を法人契約情報付きで登録できる")
    void registerCorporateShipperWithContractInfo() {
        ShipperId id = ShipperId.generate();
        ShipperName name = new ShipperName("株式会社テスト");
        ContactInfo contact = new ContactInfo("corp@example.com", "03-0000-0000");
        CorporateContractInfo corp = new CorporateContractInfo("CONTRACT-001", new BigDecimal("10.0"));

        Shipper shipper = Shipper.registerCorporate(id, name, contact, corp);

        assertThat(shipper.getCategory()).isEqualTo(CustomerCategory.CORPORATE);
        assertThat(shipper.getCorporateContractInfo()).isNotNull();
        assertThat(shipper.getCorporateContractInfo().contractNumber()).isEqualTo("CONTRACT-001");
        assertThat(shipper.getCorporateContractInfo().discountRate()).isEqualByComparingTo("10.0");
    }

    @Nested
    @DisplayName("CorporateContractInfo バリデーション")
    class CorporateContractInfoValidation {

        @Test
        @DisplayName("割引率 0% は許容される")
        void acceptZeroDiscountRate() {
            assertThatCode(() -> new CorporateContractInfo("C001", BigDecimal.ZERO))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("割引率 30% は許容される")
        void acceptMaxDiscountRate() {
            assertThatCode(() -> new CorporateContractInfo("C001", new BigDecimal("30")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("割引率が負の値は受け入れない")
        void rejectNegativeDiscountRate() {
            assertThatThrownBy(() -> new CorporateContractInfo("C001", new BigDecimal("-0.1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("割引率が 30% 超は受け入れない")
        void rejectDiscountRateOver30() {
            assertThatThrownBy(() -> new CorporateContractInfo("C001", new BigDecimal("30.1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("契約番号が空は受け入れない")
        void rejectEmptyContractNumber() {
            assertThatThrownBy(() -> new CorporateContractInfo("", new BigDecimal("10")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("法人契約情報 null で法人荷主を登録しようとすると例外")
        void rejectNullCorporateContractInfo() {
            assertThatThrownBy(() -> Shipper.registerCorporate(
                    ShipperId.generate(), new ShipperName("テスト"),
                    new ContactInfo("t@t.com", null), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
