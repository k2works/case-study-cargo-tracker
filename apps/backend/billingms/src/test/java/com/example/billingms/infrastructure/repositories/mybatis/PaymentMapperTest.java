package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.model.BillingStatus;
import com.example.billingms.domain.projections.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PaymentMapper} の MyBatis 統合テスト（IT8 T5.2 / ADR-0019）。
 *
 * <p>Flyway で payment テーブルを生成し、insertPayment → updatePaymentDetail → findByInvoiceId
 * の経路で payment_method / external_reference 補完 SQL を検証する。
 * {@code @AutoConfigureTestDatabase(replace = NONE)} で local-h2 の H2 設定をそのまま使う。</p>
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("local-h2")
class PaymentMapperTest {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private InvoiceSummaryMapper invoiceSummaryMapper;

    /** payment.invoice_id の FK 制約を満たすための事前 INSERT。 */
    private void seedInvoice(String invoiceId, String shipperId, BigDecimal amount) {
        invoiceSummaryMapper.insertInvoice(
                invoiceId, "B-" + invoiceId, shipperId,
                amount, "JPY", BillingStatus.INVOICED.name());
    }

    @Test
    @DisplayName("ADR-0019 T5.2: updatePaymentDetail で payment_method / external_reference が補完される")
    void updatePaymentDetailUpdatesMethodAndReference() {
        seedInvoice("INV-T52-1", "S-T52-1", new BigDecimal("330000"));
        // 先に shared PaymentRecordedEvent 経由の INSERT を再現（method/ref は null）
        paymentMapper.insertPayment(
                "PAY-T52-1",
                "INV-T52-1",
                new BigDecimal("330000"),
                "JPY",
                LocalDateTime.of(2026, 9, 15, 14, 30),
                null,
                null);

        // PaymentDetailRecorded 受信を再現した UPDATE
        int updated = paymentMapper.updatePaymentDetail(
                "PAY-T52-1", "BANK_TRANSFER", "TXN-2026-0001");

        assertThat(updated).isEqualTo(1);
        List<Payment> result = paymentMapper.findByInvoiceId("INV-T52-1");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(result.get(0).getExternalReference()).isEqualTo("TXN-2026-0001");
    }

    @Test
    @DisplayName("ADR-0019 T5.2: 該当 paymentId が存在しない場合は更新件数 0（影響なし）")
    void updatePaymentDetailReturnsZeroWhenPaymentNotFound() {
        int updated = paymentMapper.updatePaymentDetail(
                "PAY-NON-EXISTENT", "MANUAL", "TXN-X");

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("ADR-0019 T5.2: paymentMethod のみ補完（externalReference は null のまま）")
    void updatePaymentDetailWithMethodOnly() {
        seedInvoice("INV-T52-2", "S-T52-2", new BigDecimal("150000"));
        paymentMapper.insertPayment(
                "PAY-T52-2",
                "INV-T52-2",
                new BigDecimal("150000"),
                "JPY",
                LocalDateTime.of(2026, 9, 16, 10, 0),
                null,
                null);

        paymentMapper.updatePaymentDetail("PAY-T52-2", "CREDIT_CARD", null);

        Payment p = paymentMapper.findByInvoiceId("INV-T52-2").get(0);
        assertThat(p.getPaymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(p.getExternalReference()).isNull();
    }
}
