package com.example.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 請求の識別子と税率。
 *
 * <p><strong>空の識別子を作れないようにする。</strong>空のまま作れると、
 * どの予約に対する請求か分からない行が生まれ、あとから辿れない。
 */
@DisplayName("請求の識別子と税率")
class BillingIdentifiersTest {

    @Nested
    @DisplayName("請求番号")
    class Invoices {

        @Test
        @DisplayName("空の請求番号は作れない")
        void rejectsBlankValues() {
            assertThatThrownBy(() -> InvoiceId.of("  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InvoiceId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("採番された番号を持つ")
        void keepsTheNumberedValue() {
            assertThat(InvoiceId.of("INV-2026000001").value()).isEqualTo("INV-2026000001");
        }
    }

    @Nested
    @DisplayName("予約参照")
    class Bookings {

        @Test
        @DisplayName("空の予約番号は作れない")
        void rejectsBlankValues() {
            assertThatThrownBy(() -> BillingBookingId.of(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> BillingBookingId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("予約番号を持つ")
        void keepsTheValue() {
            assertThat(BillingBookingId.of("BKG-2026000007").value())
                    .isEqualTo("BKG-2026000007");
        }
    }

    @Nested
    @DisplayName("荷主参照")
    class Shippers {

        /** <strong>法人かどうかの判定を内包する</strong>（正典の要素表）。 */
        @Test
        @DisplayName("法人か個人かを自分で答える")
        void tellsWhetherTheShipperIsCorporate() {
            assertThat(BillingShipperId.corporate("1", "丸紅商事株式会社").isCorporate()).isTrue();
            assertThat(BillingShipperId.individual("2", "山田太郎").isCorporate()).isFalse();
        }

        @Test
        @DisplayName("空の荷主 ID は作れない")
        void rejectsBlankValues() {
            assertThatThrownBy(() -> BillingShipperId.corporate(" ", "名前"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> BillingShipperId.individual(null, "名前"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("税率")
    class Taxes {

        /** 既定は 10%（[ADR-027] 決定 8）。 */
        @Test
        @DisplayName("既定の税率は 10% である")
        void usesTenPercentByDefault() {
            assertThat(TaxRate.standard().value()).isEqualByComparingTo("0.1000");
            assertThat(TaxRate.standard().percentage()).isEqualByComparingTo("10");
        }

        /**
         * <strong>国が異なれば免税</strong>（決定 8 の改訂・輸出免税）。
         *
         * <p><strong>対で見る。</strong>免税だけを見ると、常に 0% を返す実装でも緑になる。
         */
        @Test
        @DisplayName("国が異なれば免税、同じ国なら課税")
        void exemptsInternationalTransport() {
            assertThat(TaxRate.forRoute("JP", "US").exempted())
                    .as("国際輸送に消費税が付いている。本来かからない 10% を請求し続ける")
                    .isTrue();
            assertThat(TaxRate.forRoute("JP", "US").value()).isEqualByComparingTo("0");
            assertThat(TaxRate.forRoute("JP", "JP").exempted())
                    .as("国内輸送が免税になっている。取るべき消費税を取っていない")
                    .isFalse();
            assertThat(TaxRate.forRoute("JP", "JP").value()).isEqualByComparingTo("0.1000");
        }

        /**
         * <strong>不明なら課税に倒す。</strong>免税に倒すと、国コードを引けない不具合が
         * 「消費税を取り忘れる」形で出て、気づくのは税務調査のときになる。
         */
        @Test
        @DisplayName("国が分からなければ課税に倒す")
        void fallsBackToTaxableWhenTheCountryIsUnknown() {
            assertThat(TaxRate.forRoute(null, "US").exempted()).isFalse();
            assertThat(TaxRate.forRoute("JP", null).exempted()).isFalse();
        }

        @Test
        @DisplayName("課税額を出せる")
        void calculatesTheTax() {
            assertThat(TaxRate.standard().taxOf(Money.yen(new BigDecimal("378000"))))
                    .isEqualTo(Money.yen(new BigDecimal("37800")));
        }

        /** 端数は 1 円に丸まる（決定 2）。 */
        @Test
        @DisplayName("課税額の端数は 1 円に丸まる")
        void roundsTheTax() {
            assertThat(TaxRate.standard().taxOf(Money.yen(new BigDecimal("12345"))))
                    .isEqualTo(Money.yen(new BigDecimal("1235")));
        }

        @Test
        @DisplayName("負の税率は作れない")
        void rejectsNegativeRates() {
            BigDecimal negative = new BigDecimal("-0.1");

            assertThatThrownBy(() -> TaxRate.of(negative))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TaxRate.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 任意の税率も作れる（US23 で税区分を扱うときの足場）。 */
        @Test
        @DisplayName("税率を指定して作れる")
        void acceptsAnExplicitRate() {
            assertThat(TaxRate.of(new BigDecimal("0.0800")).percentage())
                    .isEqualByComparingTo("8");
        }
    }

    @Nested
    @DisplayName("明細")
    class LineItems {

        @Test
        @DisplayName("内容と金額を持つ")
        void keepsDescriptionAndAmount() {
            InvoiceLineItem item = InvoiceLineItem.of("遅延による減額",
                    Money.yen(new BigDecimal("-10000")));

            assertThat(item.description()).isEqualTo("遅延による減額");
            assertThat(item.amount().isNegative()).isTrue();
        }

        @Test
        @DisplayName("金額の無い明細は作れない")
        void requiresAnAmount() {
            assertThatThrownBy(() -> InvoiceLineItem.of("遅延による減額", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("キャンセル料の有無")
    class CancellationFees {

        /** <strong>0 円のキャンセル料は「無い」ではなく「0 円」である。</strong> */
        @Test
        @DisplayName("料率が 0 なら、料金は発生しない")
        void tellsWhetherAFeeApplies() {
            Money base = Money.yen(new BigDecimal("400000"));

            assertThat(CancellationFee.forStatus(CancelledAtStatus.PRELIMINARY, base).applies())
                    .isFalse();
            assertThat(CancellationFee.forStatus(CancelledAtStatus.IN_TRANSIT, base).applies())
                    .isTrue();
        }
    }
}
