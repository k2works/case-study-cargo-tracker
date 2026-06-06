package com.example.billingms.infrastructure.init;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.commands.RecordPaymentCommand;
import com.example.billingms.domain.model.TransportRecord;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 開発環境用のシードデータ投入（local-h2 / local-docker プロファイル）。
 *
 * <p>ダッシュボードの請求一覧／督促一覧メニューを空でない状態にするため、
 * ApplicationReadyEvent 後に CommandGateway 経由でサンプル請求を投入する。
 * 状態の異なる 3 件（CALCULATED / ISSUED / OVERDUE → PAID）を用意。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private static final String INVOICE_CALCULATED = "INV-SAMPLE001";
    private static final String INVOICE_ISSUED = "INV-SAMPLE002";
    private static final String INVOICE_OVERDUE = "INV-SAMPLE003";

    private final CommandGateway commandGateway;
    private final InvoiceSummaryMapper invoiceSummaryMapper;

    public DevDataSeeder(
            CommandGateway commandGateway,
            InvoiceSummaryMapper invoiceSummaryMapper
    ) {
        this.commandGateway = commandGateway;
        this.invoiceSummaryMapper = invoiceSummaryMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("DevDataSeeder: seeding billingms sample invoices (local-h2/local-docker)");
        try {
            TransportRecord transport = new TransportRecord(
                    new BigDecimal("8500.00"),
                    new BigDecimal("2000.00"),
                    "GENERAL",
                    4,
                    "JPY"
            );

            seedCalculated(INVOICE_CALCULATED, "BK-001", "SHIP-001", transport);
            seedIssued(INVOICE_ISSUED, "BK-002", "SHIP-002", transport);
            seedOverdue(INVOICE_OVERDUE, "BK-003", "SHIP-003", transport);

            log.info("DevDataSeeder: billingms seed complete");
        } catch (Exception e) {
            log.warn("DevDataSeeder: billingms seed failed (continuing)", e);
        }
    }

    private void seedCalculated(String invoiceId, String bookingId, String shipperId, TransportRecord transport) {
        if (invoiceSummaryMapper.findByInvoiceId(invoiceId) != null) {
            log.debug("DevDataSeeder: invoice {} already exists, skipping", invoiceId);
            return;
        }
        if (!send(new CalculateInvoiceCommand(invoiceId, bookingId, shipperId, transport), invoiceId)) {
            return;
        }
        log.info("DevDataSeeder: seeded invoice {} (CALCULATED)", invoiceId);
    }

    private void seedIssued(String invoiceId, String bookingId, String shipperId, TransportRecord transport) {
        if (invoiceSummaryMapper.findByInvoiceId(invoiceId) != null) {
            log.debug("DevDataSeeder: invoice {} already exists, skipping", invoiceId);
            return;
        }
        if (!send(new CalculateInvoiceCommand(invoiceId, bookingId, shipperId, transport), invoiceId)) {
            return;
        }
        if (!send(new IssueInvoiceCommand(invoiceId), invoiceId)) {
            return;
        }
        log.info("DevDataSeeder: seeded invoice {} (ISSUED)", invoiceId);
    }

    private void seedOverdue(String invoiceId, String bookingId, String shipperId, TransportRecord transport) {
        if (invoiceSummaryMapper.findByInvoiceId(invoiceId) != null) {
            log.debug("DevDataSeeder: invoice {} already exists, skipping", invoiceId);
            return;
        }
        if (!send(new CalculateInvoiceCommand(invoiceId, bookingId, shipperId, transport), invoiceId)) {
            return;
        }
        if (!send(new IssueInvoiceCommand(invoiceId), invoiceId)) {
            return;
        }
        if (!send(new MarkOverdueCommand(invoiceId), invoiceId)) {
            return;
        }
        BigDecimal totalAmount = readTotalAmount(invoiceId);
        send(new RecordPaymentCommand(
                invoiceId,
                UUID.randomUUID().toString(),
                totalAmount,
                transport.currency(),
                LocalDateTime.now().minusHours(1),
                "BANK_TRANSFER",
                "DEV-SEED-REF"
        ), invoiceId);
        log.info("DevDataSeeder: seeded invoice {} (OVERDUE -> PAID)", invoiceId);
    }

    private BigDecimal readTotalAmount(String invoiceId) {
        InvoiceSummary summary = invoiceSummaryMapper.findByInvoiceId(invoiceId);
        if (summary != null && summary.getTotalAmount() != null) {
            return summary.getTotalAmount();
        }
        return new BigDecimal("100000");
    }

    private boolean send(Object command, String invoiceId) {
        try {
            commandGateway.send(command).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed invoice {} ({}): {}",
                    invoiceId, command.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
}
