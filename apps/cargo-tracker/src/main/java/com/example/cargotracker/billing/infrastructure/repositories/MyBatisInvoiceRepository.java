package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountType;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisInvoiceRepository implements InvoiceRepository {

    private final InvoiceMapper invoiceMapper;

    public MyBatisInvoiceRepository(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    public void save(Invoice invoice) {
        invoiceMapper.insert(toRecord(invoice));
    }

    @Override
    public void update(Invoice invoice) {
        invoiceMapper.update(toRecord(invoice));
    }

    @Override
    public Optional<Invoice> findByInvoiceId(InvoiceId invoiceId) {
        return Optional.ofNullable(invoiceMapper.findByInvoiceNumber(invoiceId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<Invoice> findByBookingId(String bookingId) {
        return Optional.ofNullable(invoiceMapper.findByBookingId(bookingId))
                .map(this::toDomain);
    }

    @Override
    public List<Invoice> findAll() {
        return invoiceMapper.findAll().stream().map(this::toDomain).toList();
    }

    private InvoiceRecord toRecord(Invoice invoice) {
        InvoiceRecord record = new InvoiceRecord();
        record.setInvoiceNumber(invoice.getInvoiceId().value());
        record.setBookingId(invoice.getBookingId());
        record.setTotalAmountValue(invoice.getTotalAmountValue());
        record.setDiscountType(invoice.getDiscountPolicy().type().name());
        record.setDiscountRate(invoice.getDiscountPolicy().rate());
        record.setDiscountedAmountValue(invoice.getDiscountedAmountValue());
        record.setPaymentStatus(invoice.getPaymentStatus().name());
        record.setDueDate(invoice.getDueDate());
        return record;
    }

    private Invoice toDomain(InvoiceRecord record) {
        DiscountType discountType = DiscountType.valueOf(record.getDiscountType());
        DiscountPolicy discountPolicy = new DiscountPolicy(discountType, record.getDiscountRate());
        PaymentStatus paymentStatus = PaymentStatus.valueOf(record.getPaymentStatus());
        return Invoice.reconstruct(
                InvoiceId.of(record.getInvoiceNumber()),
                record.getBookingId(),
                record.getTotalAmountValue(),
                discountPolicy,
                record.getDiscountedAmountValue(),
                paymentStatus,
                record.getDueDate()
        );
    }
}
