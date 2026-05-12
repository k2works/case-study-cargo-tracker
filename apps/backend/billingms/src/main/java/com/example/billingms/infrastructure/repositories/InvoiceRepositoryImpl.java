package com.example.billingms.infrastructure.repositories;

import com.example.billingms.domain.model.aggregates.Invoice;
import com.example.billingms.domain.model.aggregates.InvoiceLineItem;
import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.model.valueobjects.PaymentStatus;
import com.example.billingms.domain.ports.InvoiceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * InvoiceRepository の MyBatis 実装
 */
@Repository
public class InvoiceRepositoryImpl implements InvoiceRepository {

    private final InvoiceMapper mapper;

    public InvoiceRepositoryImpl(InvoiceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Invoice save(Invoice invoice) {
        InvoiceRecord invoiceRecord = toRecord(invoice);
        mapper.insert(invoiceRecord);

        for (InvoiceLineItem item : invoice.getLineItems()) {
            InvoiceLineItemRecord itemRecord = toLineItemRecord(invoiceRecord.getId(), item);
            mapper.insertLineItem(itemRecord);
        }

        return findById(invoiceRecord.getId()).orElseThrow();
    }

    @Override
    public Optional<Invoice> findById(Long id) {
        return mapper.findById(id)
                .map(r -> {
                    List<InvoiceLineItemRecord> items = mapper.findLineItemsByInvoiceId(r.getId());
                    return toEntity(r, items);
                });
    }

    @Override
    public Optional<Invoice> findByBookingId(String bookingId) {
        return mapper.findByBookingId(bookingId)
                .map(r -> {
                    List<InvoiceLineItemRecord> items = mapper.findLineItemsByInvoiceId(r.getId());
                    return toEntity(r, items);
                });
    }

    @Override
    public void update(Invoice invoice) {
        InvoiceRecord invoiceRecord = toRecord(invoice);
        invoiceRecord.setId(invoice.getId());
        mapper.updateStatus(invoiceRecord);
    }

    private InvoiceRecord toRecord(Invoice invoice) {
        InvoiceRecord invoiceRecord = new InvoiceRecord();
        invoiceRecord.setInvoiceNumber(invoice.getInvoiceNumber());
        invoiceRecord.setBookingId(invoice.getBookingId());
        invoiceRecord.setShipperId(invoice.getShipperId());
        invoiceRecord.setBaseAmountValue(invoice.getBaseAmount().toLong());
        invoiceRecord.setBaseAmountCurrency(invoice.getBaseAmount().currency());
        invoiceRecord.setFinalAmountValue(invoice.getFinalAmount().toLong());
        invoiceRecord.setFinalAmountCurrency(invoice.getFinalAmount().currency());
        invoiceRecord.setTaxRate(invoice.getTaxRate());
        long discountedBase = invoice.getBaseAmount().toLong() - invoice.getDiscountAmount().toLong();
        Money tax = Money.ofJpy(discountedBase).multiply(invoice.getTaxRate());
        invoiceRecord.setTaxAmountValue(tax.toLong());
        invoiceRecord.setDiscountRate(invoice.getDiscountRate());
        invoiceRecord.setDiscountAmountValue(invoice.getDiscountAmount().toLong());
        invoiceRecord.setPaidAt(invoice.getPaidAt());
        invoiceRecord.setPaymentStatus(invoice.getPaymentStatus().name());
        invoiceRecord.setIssuedAt(invoice.getIssuedAt());
        invoiceRecord.setDueDate(invoice.getDueDate());
        return invoiceRecord;
    }

    private InvoiceLineItemRecord toLineItemRecord(Long invoiceId, InvoiceLineItem item) {
        InvoiceLineItemRecord r = new InvoiceLineItemRecord();
        r.setInvoiceId(invoiceId);
        r.setDescription(item.getDescription());
        r.setAmountValue(item.getAmount().toLong());
        r.setAmountCurrency(item.getAmount().currency());
        r.setSeqNumber(item.getSeqNumber());
        return r;
    }

    private Invoice toEntity(InvoiceRecord r, List<InvoiceLineItemRecord> items) {
        List<InvoiceLineItem> lineItems = items.stream()
                .map(i -> new InvoiceLineItem(
                        i.getId(),
                        i.getDescription(),
                        new Money(java.math.BigDecimal.valueOf(i.getAmountValue()), i.getAmountCurrency()),
                        i.getSeqNumber()))
                .toList();

        Money discountAmount = r.getDiscountAmountValue() != null
                ? Money.ofJpy(r.getDiscountAmountValue())
                : Money.ofJpy(0);

        return new Invoice(
                r.getInvoiceNumber(),
                r.getBookingId(),
                r.getShipperId(),
                r.getIssuedAt(),
                r.getDueDate(),
                new Invoice.PersistedState(
                        r.getId(),
                        new Money(java.math.BigDecimal.valueOf(r.getBaseAmountValue()), r.getBaseAmountCurrency()),
                        new Money(java.math.BigDecimal.valueOf(r.getFinalAmountValue()), r.getFinalAmountCurrency()),
                        PaymentStatus.valueOf(r.getPaymentStatus()),
                        r.getDiscountRate(),
                        discountAmount,
                        r.getPaidAt(),
                        lineItems
                )
        );
    }
}
