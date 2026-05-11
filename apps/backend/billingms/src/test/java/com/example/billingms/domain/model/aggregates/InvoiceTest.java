package com.example.billingms.domain.model.aggregates;

import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.model.valueobjects.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Invoice ドメインテスト")
class InvoiceTest {

    private Invoice createInvoice() {
        return new Invoice("INV-001", "BK-001", "SH-001",
                LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("明細を追加すると基本料金が合計される")
    void shouldSumLineItemsAsBaseAmount() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));
        invoice.addLineItem(new InvoiceLineItem("距離料金", Money.ofJpy(50_000), 2));

        assertThat(invoice.getBaseAmount().toLong()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("消費税 10% を含む最終金額が計算される")
    void shouldCalculateFinalAmountWithTax() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));

        Money finalAmount = invoice.calculateFinalAmount();

        assertThat(finalAmount.toLong()).isEqualTo(110_000L);
    }

    @Test
    @DisplayName("確定操作で支払いステータスが CONFIRMED になる")
    void shouldChangeStatusToConfirmedOnConfirm() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));

        invoice.confirm();

        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("割引率を適用すると割引後の基本料金が設定される")
    void shouldApplyDiscountToBaseAmount() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));

        invoice.applyDiscount(new BigDecimal("0.10"));

        assertThat(invoice.getDiscountRate()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(invoice.getDiscountAmount().toLong()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("割引適用後の最終金額に割引が反映される")
    void shouldCalculateFinalAmountWithDiscount() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));
        invoice.applyDiscount(new BigDecimal("0.10"));

        Money finalAmount = invoice.calculateFinalAmount();

        // 割引後基本料金 90,000 + 消費税 9,000 = 99,000
        assertThat(finalAmount.toLong()).isEqualTo(99_000L);
    }

    @Test
    @DisplayName("割引率が 0 の場合は割引が適用されない")
    void shouldNotApplyDiscountWhenRateIsZero() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));
        invoice.applyDiscount(BigDecimal.ZERO);

        assertThat(invoice.getDiscountAmount().toLong()).isEqualTo(0L);
        assertThat(invoice.calculateFinalAmount().toLong()).isEqualTo(110_000L);
    }

    @Test
    @DisplayName("確定済みの請求書を再確定するとエラーになる")
    void shouldThrowWhenConfirmingAlreadyConfirmedInvoice() {
        Invoice invoice = createInvoice();
        invoice.addLineItem(new InvoiceLineItem("基本料金", Money.ofJpy(100_000), 1));
        invoice.confirm();

        assertThatThrownBy(invoice::confirm)
                .isInstanceOf(IllegalStateException.class);
    }
}
