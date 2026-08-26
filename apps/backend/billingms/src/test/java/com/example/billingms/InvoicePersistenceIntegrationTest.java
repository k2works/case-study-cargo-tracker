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
import com.example.billingms.domain.model.InvoiceCharges;
import com.example.billingms.domain.model.InvoiceLineItem;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.PaymentStatus;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.example.billingms.application.internal.AlreadyInvoicedException;
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
                BillingShipperId.corporate("1", "丸紅商事株式会社"),
                new InvoiceCharges(CHARGE, policy, fee, TaxRate.standard()),
                adjustments, Instant.parse("2027-10-01T00:00:00Z"));
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

        /**
         * <strong>発行した金額は、保存された値をそのまま返す</strong>（[ADR-027] 決定 4・
         * IT11 レビュー 高 1）。
         *
         * <p>係数から毎回計算し直していると、<strong>基準運賃や貨物種別係数を将来変えた
         * 瞬間に、過去に発行した請求書の金額が黙って変わる</strong>。請求書は荷主へ出す
         * 約束であり、出したあとに変わってはならない。
         *
         * <p>この検査は<strong>保存された列を直接書き換えて</strong>確かめる。集約を
         * 経由して確かめると、再計算していても同じ値が返るため判別できない。
         */
        @Test
        @DisplayName("発行した金額は、保存された値を返す（再計算しない）")
        void returnsThePersistedAmountsInsteadOfRecalculating() {
            Invoice issued = issue(uniqueBookingId(),
                    DiscountPolicy.forCorporate(DiscountRate.of(new BigDecimal("0.1000"))),
                    List.of(), null);
            invoices.save(issued);

            // 保存された金額だけを別の値に書き換える（係数はそのまま）
            jdbcTemplate.update("""
                    UPDATE invoice
                       SET base_amount_value = 999999, discount_amount_value = 99999,
                           tax_amount = 90000, total_amount_value = 990000
                     WHERE invoice_number = ?
                    """, issued.invoiceId().value());

            Invoice restored = invoices.findById(issued.invoiceId().value()).orElseThrow();

            assertThat(restored.baseAmount().amount())
                    .as("基本料金を係数から計算し直している。基準運賃を変えると過去の請求書が変わる")
                    .isEqualByComparingTo("999999");
            assertThat(restored.discountAmount().amount())
                    .as("割引額を計算し直している")
                    .isEqualByComparingTo("99999");
            assertThat(restored.taxAmount().amount())
                    .as("消費税を計算し直している。税率を変えると過去の請求書が変わる")
                    .isEqualByComparingTo("90000");
            assertThat(restored.totalAmount().amount())
                    .as("合計を計算し直している")
                    .isEqualByComparingTo("990000");
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
         * <p><strong>制約と集約の両方で守る</strong>——集約だけだと同時に 2 回押された
         * ときに通る（発行済みかを見てから書くまでのあいだに、もう 1 本が書き込む）。
         *
         * <p><strong>制約に当たったときも、断る理由は同じである。</strong>
         * `DuplicateKeyException` のまま外へ出すと画面に 500 が出て、経理担当者には
         * 「壊れた」としか見えない——実際には「すでに発行済み」であり、409 で
         * 伝えるべき話である。先に押した側は成功しており、待っても変わらない。
         */
        @Test
        @DisplayName("同じ予約への 2 通目は、すでに発行済みとして断られる")
        void rejectsASecondInvoiceForTheSameBooking() {
            String bookingId = uniqueBookingId();
            invoices.save(issue(bookingId, DiscountPolicy.none(), List.of(), null));

            assertThatThrownBy(() ->
                    invoices.save(issue(bookingId, DiscountPolicy.none(), List.of(), null)))
                    .as("同じ予約に 2 通目が通っている。荷主に二重で請求することになる")
                    .isInstanceOf(AlreadyInvoicedException.class)
                    .hasMessageContaining(bookingId);
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
