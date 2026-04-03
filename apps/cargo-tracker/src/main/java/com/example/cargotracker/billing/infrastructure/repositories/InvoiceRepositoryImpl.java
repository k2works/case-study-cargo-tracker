package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InvoiceRepositoryImpl implements InvoiceRepository {

    private final InvoiceMapper invoiceMapper;

    public InvoiceRepositoryImpl(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    public void save(Invoice invoice) {
        LocalDateTime now = LocalDateTime.now();
        String id = invoice.getId().value().toString();

        InvoiceRecord existing = invoiceMapper.findById(id);
        if (existing == null) {
            InvoiceRecord row = new InvoiceRecord(
                    id,
                    invoice.getBookingId(),
                    invoice.getFreightChargeId(),
                    invoice.getAmount(),
                    invoice.getDueDate(),
                    invoice.getPaymentStatus().name(),
                    now,
                    now
            );
            invoiceMapper.insert(row);
        } else {
            InvoiceRecord row = new InvoiceRecord(
                    id,
                    invoice.getBookingId(),
                    invoice.getFreightChargeId(),
                    invoice.getAmount(),
                    invoice.getDueDate(),
                    invoice.getPaymentStatus().name(),
                    existing.createdAt(),
                    now
            );
            invoiceMapper.update(row);
        }
    }

    @Override
    public Optional<Invoice> findById(InvoiceId id) {
        InvoiceRecord row = invoiceMapper.findById(id.value().toString());
        return Optional.ofNullable(row).map(this::toInvoice);
    }

    @Override
    public Optional<Invoice> findByFreightChargeId(String freightChargeId) {
        InvoiceRecord row = invoiceMapper.findByFreightChargeId(freightChargeId);
        return Optional.ofNullable(row).map(this::toInvoice);
    }

    @Override
    public List<Invoice> findAll() {
        return invoiceMapper.findAll().stream()
                .map(this::toInvoice)
                .toList();
    }

    private Invoice toInvoice(InvoiceRecord row) {
        return Invoice.reconstitute(
                new InvoiceId(UUID.fromString(row.id())),
                row.bookingId(),
                row.freightChargeId(),
                row.amount(),
                row.dueDate(),
                PaymentStatus.valueOf(row.paymentStatus())
        );
    }
}
