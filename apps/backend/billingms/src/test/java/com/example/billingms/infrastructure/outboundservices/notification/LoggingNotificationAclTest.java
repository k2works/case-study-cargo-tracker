package com.example.billingms.infrastructure.outboundservices.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link LoggingNotificationAcl} の単体テスト（US23、IT7 T4.4）。
 *
 * <p>ログ出力スタブのため、副作用検証は最小限。例外を投げずに完了することを担保する。
 * 実メール送信は IT8 SendGrid 統合で WireMock + 結合テストで検証する。</p>
 */
class LoggingNotificationAclTest {

    private final LoggingNotificationAcl acl = new LoggingNotificationAcl();

    @Test
    @DisplayName("US23: notifyInvoiceIssued は例外を投げない")
    void notifyInvoiceIssued正常終了() {
        assertThatCode(() -> acl.notifyInvoiceIssued(
                "INV-001", "S-001", "INV-20260901-0001",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("330000")
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("US23: notifyPaymentReceived は例外を投げない")
    void notifyPaymentReceived正常終了() {
        assertThatCode(() -> acl.notifyPaymentReceived(
                "INV-001", "S-001", "PAY-001", new BigDecimal("330000")
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("US23: notifyOverdue は例外を投げない")
    void notifyOverdue正常終了() {
        assertThatCode(() -> acl.notifyOverdue("INV-001", "S-001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("US23: null 引数でも例外を投げない（ログのみ）")
    void null引数でも安全() {
        assertThatCode(() -> acl.notifyInvoiceIssued(null, null, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> acl.notifyPaymentReceived(null, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> acl.notifyOverdue(null, null))
                .doesNotThrowAnyException();
    }
}
