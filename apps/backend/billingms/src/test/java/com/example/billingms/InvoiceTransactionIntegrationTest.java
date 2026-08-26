package com.example.billingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.example.billingms.application.internal.AdjustmentCommand;
import com.example.billingms.application.internal.CalculateChargeUseCase;
import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.billingms.application.port.BillingSnapshotFinder;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.infrastructure.persistence.InvoiceLineItemMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 精算書の発行はひとまとまりである（IT11 レビュー 高・xp-architect）。
 *
 * <p>精算書と明細は別々の行に書く。<strong>明細の途中で失敗したとき、精算書だけが
 * 残ってはならない</strong>——調整明細を欠いた請求書が「確定済み」として残ると、
 * [ADR-027] 決定 4 により金額を動かす手段が無く、{@code booking_id} は UNIQUE なので
 * 出し直しもできない。<strong>自力で復旧できない状態になる。</strong>
 */
@SpringBootTest
@ActiveProfiles("integration")
@DisplayName("精算書の発行はひとまとまりである")
class InvoiceTransactionIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private CalculateChargeUseCase calculateCharge;

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 明細の書き込みだけを失敗させる。 */
    @MockitoSpyBean
    private InvoiceLineItemMapper lineItems;

    /** 料金算出の入力は差し替える（bookingms を呼ばない）。 */
    @MockitoSpyBean
    private BillingSnapshotFinder snapshots;

    @Test
    @DisplayName("明細の書き込みが失敗したら、精算書の行も残らない")
    void rollsBackTheInvoiceWhenALineItemFails() {
        String bookingId = "BKG-TX" + System.nanoTime() % 1_000_000;
        org.mockito.Mockito.doReturn(Optional.of(new BillableCargoSnapshot(
                        bookingId, "DELIVERED", "1", "丸紅商事株式会社", true,
                        new BigDecimal("0.1000"), new BigDecimal("4200"), "GENERAL",
                        "Tokyo", "Los Angeles", 2,
                        Instant.parse("2027-09-26T00:00:00Z"), null, null)))
                .when(snapshots).findBillable(bookingId);

        doThrow(new org.springframework.dao.DataIntegrityViolationException("明細の書き込みに失敗"))
                .when(lineItems).insert(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> calculateCharge.confirm(bookingId,
                List.of(new AdjustmentCommand("遅延による減額", new BigDecimal("-10000")))))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM invoice WHERE booking_id = ?", Integer.class,
                        bookingId))
                .as("明細を欠いた精算書が確定済みで残っている。決定 4 により金額を動かせず、"
                        + "booking_id は UNIQUE なので出し直しもできない")
                .isZero();
        assertThat(invoices.existsForBooking(bookingId))
                .as("発行済みとして扱われると、この予約は二度と精算できない")
                .isFalse();
    }
}
