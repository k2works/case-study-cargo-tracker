package com.example.cargotracker.billing.domain.model.repository;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository {

    void save(Invoice invoice);

    void update(Invoice invoice);

    Optional<Invoice> findByInvoiceId(InvoiceId invoiceId);

    Optional<Invoice> findByBookingId(String bookingId);

    List<Invoice> findAll();
}
