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
        InvoiceRecord record = toRecord(invoice);
        mapper.insert(record);

        for (InvoiceLineItem item : invoice.getLineItems()) {
            InvoiceLineItemRecord itemRecord = toLineItemRecord(record.getId(), item);
            mapper.insertLineItem(itemRecord);
        }

        return findById(record.getId()).orElseThrow();
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
        InvoiceRecord record = toRecord(invoice);
        record.setId(invoice.getId());
        mapper.updateStatus(record);
    }

    private InvoiceRecord toRecord(Invoice invoice) {
        InvoiceRecord record = new InvoiceRecord();
        record.setInvoiceNumber(invoice.getInvoiceNumber());
        record.setBookingId(invoice.getBookingId());
        record.setShipperId(invoice.getShipperId());
        record.setBaseAmountValue(invoice.getBaseAmount().toLong());
        record.setBaseAmountCurrency(invoice.getBaseAmount().currency());
        record.setFinalAmountValue(invoice.getFinalAmount().toLong());
        record.setFinalAmountCurrency(invoice.getFinalAmount().currency());
        record.setTaxRate(invoice.getTaxRate());
        Money tax = invoice.getBaseAmount().multiply(invoice.getTaxRate());
        record.setTaxAmountValue(tax.toLong());
        record.setDiscountRate(invoice.getDiscountRate());
        record.setDiscountAmountValue(invoice.getDiscountAmount().toLong());
        record.setPaidAt(invoice.getPaidAt());
        record.setPaymentStatus(invoice.getPaymentStatus().name());
        record.setIssuedAt(invoice.getIssuedAt());
        record.setDueDate(invoice.getDueDate());
        return record;
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

        return new Invoice(
                r.getId(),
                r.getInvoiceNumber(),
                r.getBookingId(),
                r.getShipperId(),
                new Money(java.math.BigDecimal.valueOf(r.getBaseAmountValue()), r.getBaseAmountCurrency()),
                new Money(java.math.BigDecimal.valueOf(r.getFinalAmountValue()), r.getFinalAmountCurrency()),
                r.getTaxRate(),
                PaymentStatus.valueOf(r.getPaymentStatus()),
                r.getIssuedAt(),
                r.getDueDate(),
                lineItems
        );
    }
}
