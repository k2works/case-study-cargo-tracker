package com.example.billingms.config;

import com.example.billingms.domain.model.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BillingProperties} の不変条件テスト（IT7 review 中対応 / ハードコード除去）。
 *
 * <p>record コンストラクタの validation が失敗時に {@link IllegalArgumentException} を
 * 投げることを検証。Spring Boot の {@code @ConfigurationProperties} 読み込み時に
 * 不正な設定値を early-fail させるための安全網。</p>
 */
class BillingPropertiesTest {

    private static final BillingProperties.Overdue VALID_OVERDUE =
            new BillingProperties.Overdue("0 0 9 * * *", "Asia/Tokyo");

    @Test
    @DisplayName("デフォルト構成（30 日 + Asia/Tokyo + 法人割引テンプレート）で正常生成")
    void デフォルト構成で生成可能() {
        BillingProperties props = new BillingProperties(30, Map.of(), VALID_OVERDUE, "法人割引（%d%%）", null);

        assertThat(props.paymentDueDays()).isEqualTo(30);
        assertThat(props.paymentDueDaysByType()).isEmpty();
        assertThat(props.overdue().cron()).isEqualTo("0 0 9 * * *");
        assertThat(props.overdue().zone()).isEqualTo("Asia/Tokyo");
        assertThat(props.discountDescription()).isEqualTo("法人割引（%d%%）");
    }

    @Test
    @DisplayName("paymentDueDays が 0 以下だと IllegalArgumentException")
    void paymentDueDaysが非正値で例外() {
        assertThatThrownBy(() -> new BillingProperties(0, Map.of(), VALID_OVERDUE, "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentDueDays");
        assertThatThrownBy(() -> new BillingProperties(-1, Map.of(), VALID_OVERDUE, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("overdue が null だと IllegalArgumentException")
    void overdueがnullで例外() {
        assertThatThrownBy(() -> new BillingProperties(30, Map.of(), null, "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overdue");
    }

    @Test
    @DisplayName("discountDescription が null / 空文字 / 空白だと IllegalArgumentException")
    void discountDescriptionが空で例外() {
        assertThatThrownBy(() -> new BillingProperties(30, Map.of(), VALID_OVERDUE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingProperties(30, Map.of(), VALID_OVERDUE, "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingProperties(30, Map.of(), VALID_OVERDUE, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IT8 T1.9: paymentDueDaysByType の値が 0 以下だと IllegalArgumentException")
    void T19_Map内に非正値があると例外() {
        assertThatThrownBy(() -> new BillingProperties(
                30, Map.of(ShipperType.CORPORATE, 0), VALID_OVERDUE, "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentDueDaysByType");
    }

    @Test
    @DisplayName("IT8 T1.9: paymentDueDaysFor は Map 設定値を返し、未設定なら default を返す")
    void T19_paymentDueDaysFor() {
        BillingProperties props = new BillingProperties(
                30,
                Map.of(ShipperType.CORPORATE, 60),
                VALID_OVERDUE,
                "x",
                null);
        assertThat(props.paymentDueDaysFor(ShipperType.CORPORATE)).isEqualTo(60);
        assertThat(props.paymentDueDaysFor(ShipperType.INDIVIDUAL)).isEqualTo(30);
        assertThat(props.paymentDueDaysFor(null)).isEqualTo(30);
    }

    @Test
    @DisplayName("Overdue.cron が null / 空だと IllegalArgumentException")
    void overdueCronが空で例外() {
        assertThatThrownBy(() -> new BillingProperties.Overdue(null, "Asia/Tokyo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron");
        assertThatThrownBy(() -> new BillingProperties.Overdue("  ", "Asia/Tokyo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Overdue.zone が null / 空だと IllegalArgumentException")
    void overdueZoneが空で例外() {
        assertThatThrownBy(() -> new BillingProperties.Overdue("0 0 9 * * *", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zone");
        assertThatThrownBy(() -> new BillingProperties.Overdue("0 0 9 * * *", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NET60 等の長期サイト（60 日）でも構成可能（IT8 拡張準備）")
    void 長期サイト構成可能() {
        BillingProperties props = new BillingProperties(60, Map.of(), VALID_OVERDUE, "法人割引（%d%%）", null);
        assertThat(props.paymentDueDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("英語テンプレート / 別 timezone でも構成可能（多言語化・海外運用準備）")
    void 多言語_他timezoneで構成可能() {
        BillingProperties props = new BillingProperties(
                30,
                Map.of(),
                new BillingProperties.Overdue("0 0 10 * * *", "America/New_York"),
                "Corporate discount (%d%%)",
                null
        );
        assertThat(props.overdue().zone()).isEqualTo("America/New_York");
        assertThat(props.discountDescription()).isEqualTo("Corporate discount (%d%%)");
    }

    @Test
    @DisplayName("IT8 T1.8: rateTable 未指定時は defaultSettings（GENERAL/HAZARDOUS/REFRIGERATED + handlingUnitFee=1500）に正規化される")
    void T18_rateTable未指定時はdefaultsに正規化() {
        BillingProperties props = new BillingProperties(
                30, Map.of(), VALID_OVERDUE, "x", null);

        BillingProperties.RateTableSettings rt = props.rateTable();
        assertThat(rt.rates())
                .containsEntry("GENERAL", new java.math.BigDecimal("0.05"))
                .containsEntry("HAZARDOUS", new java.math.BigDecimal("0.08"))
                .containsEntry("REFRIGERATED", new java.math.BigDecimal("0.10"));
        assertThat(rt.handlingUnitFee()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("IT8 T1.8: rateTable 指定時は設定値がそのまま反映される（経理担当者の料金改定）")
    void T18_rateTable設定値が反映() {
        BillingProperties.RateTableSettings custom = new BillingProperties.RateTableSettings(
                Map.of("GENERAL", new java.math.BigDecimal("0.06")),
                new java.math.BigDecimal("2000"));
        BillingProperties props = new BillingProperties(
                30, Map.of(), VALID_OVERDUE, "x", custom);

        assertThat(props.rateTable().rates()).containsEntry("GENERAL", new java.math.BigDecimal("0.06"));
        assertThat(props.rateTable().handlingUnitFee()).isEqualByComparingTo("2000");
    }
}
