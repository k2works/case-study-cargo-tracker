package com.example.billingms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        BillingProperties props = new BillingProperties(30, VALID_OVERDUE, "法人割引（%d%%）");

        assertThat(props.paymentDueDays()).isEqualTo(30);
        assertThat(props.overdue().cron()).isEqualTo("0 0 9 * * *");
        assertThat(props.overdue().zone()).isEqualTo("Asia/Tokyo");
        assertThat(props.discountDescription()).isEqualTo("法人割引（%d%%）");
    }

    @Test
    @DisplayName("paymentDueDays が 0 以下だと IllegalArgumentException")
    void paymentDueDaysが非正値で例外() {
        assertThatThrownBy(() -> new BillingProperties(0, VALID_OVERDUE, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentDueDays");
        assertThatThrownBy(() -> new BillingProperties(-1, VALID_OVERDUE, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("overdue が null だと IllegalArgumentException")
    void overdueがnullで例外() {
        assertThatThrownBy(() -> new BillingProperties(30, null, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overdue");
    }

    @Test
    @DisplayName("discountDescription が null / 空文字 / 空白だと IllegalArgumentException")
    void discountDescriptionが空で例外() {
        assertThatThrownBy(() -> new BillingProperties(30, VALID_OVERDUE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingProperties(30, VALID_OVERDUE, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingProperties(30, VALID_OVERDUE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
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
        BillingProperties props = new BillingProperties(60, VALID_OVERDUE, "法人割引（%d%%）");
        assertThat(props.paymentDueDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("英語テンプレート / 別 timezone でも構成可能（多言語化・海外運用準備）")
    void 多言語_他timezoneで構成可能() {
        BillingProperties props = new BillingProperties(
                30,
                new BillingProperties.Overdue("0 0 10 * * *", "America/New_York"),
                "Corporate discount (%d%%)"
        );
        assertThat(props.overdue().zone()).isEqualTo("America/New_York");
        assertThat(props.discountDescription()).isEqualTo("Corporate discount (%d%%)");
    }
}
