package com.example.billingms.application.internal.commandservices;

import com.example.billingms.domain.model.aggregates.Invoice;
import com.example.billingms.domain.model.aggregates.InvoiceLineItem;
import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.ports.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 請求書コマンドサービス
 */
@Service
public class InvoiceCommandService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceCommandService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * 料金を算出し Invoice を作成する
     */
    @Transactional
    public Invoice calculate(CalculateInvoiceCommand command) {
        String invoiceNumber = "INV-" + command.bookingId();
        LocalDate today = LocalDate.now();

        Invoice invoice = new Invoice(
                invoiceNumber,
                command.bookingId(),
                command.shipperId(),
                today,
                today.plusDays(30)
        );

        AtomicInteger seq = new AtomicInteger(1);
        for (CalculateInvoiceCommand.LineItemInput item : command.lineItems()) {
            invoice.addLineItem(new InvoiceLineItem(
                    item.description(),
                    Money.ofJpy(item.amountValue()),
                    seq.getAndIncrement()
            ));
        }
        BigDecimal rate = command.discountRate() != null ? command.discountRate() : BigDecimal.ZERO;
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            invoice.applyDiscount(rate);
        }
        invoice.calculateFinalAmount();

        return invoiceRepository.save(invoice);
    }

    /**
     * 料金を確定する
     */
    @Transactional
    public Invoice confirm(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        invoice.confirm();
        invoiceRepository.update(invoice);

        return invoice;
    }
}
