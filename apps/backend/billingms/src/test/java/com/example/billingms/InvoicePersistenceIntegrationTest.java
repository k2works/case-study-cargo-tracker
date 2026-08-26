package com.example.billingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.billingms.application.port.InvoiceNumbering;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.BillingBookingId;
import com.example.billingms.domain.model.BillingShipperId;
import com.example.billingms.domain.model.CancellationFee;
import com.example.billingms.domain.model.CancelledAtStatus;
import com.example.billingms.domain.model.CargoType;
import com.example.billingms.domain.model.DiscountPolicy;
import com.example.billingms.domain.model.DiscountRate;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.InvoiceId;
import com.example.billingms.domain.model.InvoiceLineItem;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.PaymentStatus;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 精算書の永続化（US21）。
 *
 * <p><strong>実 DB で確かめる。</strong>採番（シーケンス）・二重請求の制約・明細の保存と
 * 復元は、いずれも DB の振る舞いに依存する。スタブでは<strong>列名の誤りも制約の抜けも
 * 見つからない</strong>。
 */
@SpringBootTest
@ActiveProfiles("integration")
@DisplayName("精算書の永続化")
class InvoicePersistenceIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private InvoiceNumbering numbering;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final TransportCharge CHARGE =
            TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL);

    private Invoice issue(String bookingId, DiscountPolicy policy,
            List<InvoiceLineItem> adjustments, CancellationFee fee) {
        return Invoice.issue(numbering.next(), BillingBookingId.of(bookingId),
                BillingShipperId.corporate("1"), "丸紅商事株式会社", CHARGE, policy,
                adjustments, fee, TaxRate.standard(),
                Instant.parse("2027-10-01T00:00:00Z"));
    }

    private String uniqueBookingId() {
        return "BKG-TEST" + jdbcTemplate.queryForObject(
                "SELECT NEXTVAL('invoice_number_seq')", Long.class);
    }

    @Nested
    @DisplayName("保存と復元")
    class SavingAndRestoring {

        /**
         * <strong>金額も根拠も、書いたとおりに戻る。</strong>
         *
         * <p>どこか 1 層で落としても金額そのものは出るため、<strong>根拠まで含めて
         * 突き合わせる</strong>（項目ごとの比較の積み上げは、属性が増えるたび漏れる）。
         */
        @Test
        @DisplayName("発行した精算書を、根拠ごと読み戻せる")
        void restoresTheInvoiceWithItsBasis() {
            Invoice issued = issue(uniqueBookingId(),
                    DiscountPolicy.forCorporate(DiscountRate.of(new BigDecimal("0.1000"))),
                    List.of(InvoiceLineItem.of("遅延による減額",
                            Money.yen(new BigDecimal("-10000")))),
                    null);
            invoices.save(issued);

            Invoice restored = invoices.findById(issued.invoiceId().value()).orElseThrow();

            assertThat(restored.invoiceId()).isEqualTo(issued.invoiceId());
            assertThat(restored.cargoBookingId()).isEqualTo(issued.cargoBookingId());
            assertThat(restored.shipperName())
                    .as("社名が戻っていない。請求書の宛名が読めない")
                    .isEqualTo("丸紅商事株式会社");
            assertThat(restored.charge()).isEqualTo(issued.charge());
            assertThat(restored.discountRate().value())
                    .as("割引率が戻っていない。額だけでは率を復元できない")
                    .isEqualByComparingTo("0.1000");
            assertThat(restored.lineItems()).hasSize(1);
            assertThat(restored.lineItems().get(0).description()).isEqualTo("遅延による減額");
            assertThat(restored.lineItems().get(0).amount())
                    .isEqualTo(Money.yen(new BigDecimal("-10000")));
            assertThat(restored.totalAmount()).isEqualTo(issued.totalAmount());
            assertThat(restored.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        /**
         * <strong>割引が無いことが、0% として戻らない</strong>（[ADR-012]）。
         *
         * <p>0 で埋めると、設定し忘れと「割引しない契約」が同じに見える。
         */
        @Test
        @DisplayName("割引の無い精算書は、割引率を持たないまま戻る")
        void keepsTheAbsenceOfADiscount() {
            Invoice issued = issue(uniqueBookingId(), DiscountPolicy.none(), List.of(), null);
            invoices.save(issued);

            Invoice restored = invoices.findById(issued.invoiceId().value()).orElseThrow();

            assertThat(restored.discountRate())
                    .as("割引が無いのに 0% として戻っている。契約なしと区別できない")
                    .isNull();
        }

        /** キャンセル料は算定根拠ごと戻る（US30-9）。 */
        @Test
        @DisplayName("キャンセル料を、料率と申請時の状態ごと読み戻せる")
        void restoresTheCancellationFeeWithItsBasis() {
            CancellationFee fee = CancellationFee.forStatus(CancelledAtStatus.IN_TRANSIT,
                    Money.yen(new BigDecimal("420000")));
            Invoice issued = issue(uniqueBookingId(), DiscountPolicy.none(), List.of(), fee);
            invoices.save(issued);

            Invoice restored = invoices.findById(issued.invoiceId().value()).orElseThrow();

            assertThat(restored.cancellationFee()).isNotNull();
            assertThat(restored.cancellationFee().bookingStatusAtCancel())
                    .as("申請時の状態が戻っていない。なぜその料率かを言えない")
                    .isEqualTo(CancelledAtStatus.IN_TRANSIT);
            assertThat(restored.cancellationFee().feeRate()).isEqualByComparingTo("0.3");
            assertThat(restored.cancellationFee().amount())
                    .isEqualTo(Money.yen(new BigDecimal("126000")));
        }

        /** 明細が無い精算書も戻る。 */
        @Test
        @DisplayName("明細の無い精算書も読み戻せる")
        void restoresAnInvoiceWithoutLineItems() {
            Invoice issued = issue(uniqueBookingId(), DiscountPolicy.none(), List.of(), null);
            invoices.save(issued);

            assertThat(invoices.findById(issued.invoiceId().value()).orElseThrow().lineItems())
                    .isEmpty();
        }

        @Test
        @DisplayName("見つからない精算書は空を返す")
        void returnsEmptyForAnUnknownInvoice() {
            assertThat(invoices.findById("INV-9999999999")).isEmpty();
        }
    }

    @Nested
    @DisplayName("二重請求（決定 4）")
    class DoubleInvoicing {

        /**
         * <strong>同じ予約に 2 通の請求書は出せない</strong>（正典のビジネスルール 5）。
         *
         * <p><strong>制約と集約の両方で守る</strong>——制約だけだと画面に 500 が出て、
         * 集約だけだと同時に 2 回押されたときに通る。
         */
        @Test
        @DisplayName("同じ予約への 2 通目は DB が断る")
        void rejectsASecondInvoiceForTheSameBooking() {
            String bookingId = uniqueBookingId();
            invoices.save(issue(bookingId, DiscountPolicy.none(), List.of(), null));

            assertThatThrownBy(() ->
                    invoices.save(issue(bookingId, DiscountPolicy.none(), List.of(), null)))
                    .as("同じ予約に 2 通目が通っている。荷主に二重で請求することになる")
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }

        @Test
        @DisplayName("発行済みかどうかを答えられる")
        void tellsWhetherABookingIsAlreadyInvoiced() {
            String bookingId = uniqueBookingId();

            assertThat(invoices.existsForBooking(bookingId)).isFalse();
            invoices.save(issue(bookingId, DiscountPolicy.none(), List.of(), null));
            assertThat(invoices.existsForBooking(bookingId)).isTrue();
        }
    }

    @Nested
    @DisplayName("採番")
    class Numbering {

        /**
         * <strong>DB のシーケンスに任せる</strong>（[ADR-011] と同じ形）。
         *
         * <p>MAX+1 の自前採番は、同時に 2 件発行されたときに衝突する。
         */
        @Test
        @DisplayName("請求番号は重複しない")
        void issuesDistinctNumbers() {
            InvoiceId first = numbering.next();
            InvoiceId second = numbering.next();

            assertThat(first).isNotEqualTo(second);
            assertThat(first.value()).matches("INV-\\d{4}\\d{6}");
        }
    }
}
