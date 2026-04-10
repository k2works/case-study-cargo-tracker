package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public List<Invoice> findAll() {
        return invoiceRepository.findAll();
    }

    public Optional<Invoice> findByInvoiceId(String invoiceId) {
        return invoiceRepository.findByInvoiceId(InvoiceId.of(invoiceId));
    }

    public Optional<Invoice> findByBookingId(String bookingId) {
        return invoiceRepository.findByBookingId(bookingId);
    }
}
