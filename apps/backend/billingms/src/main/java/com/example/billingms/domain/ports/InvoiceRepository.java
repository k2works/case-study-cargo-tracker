package com.example.billingms.domain.ports;

import com.example.billingms.domain.model.aggregates.Invoice;

import java.util.Optional;

/**
 * 請求書リポジトリポート
 */
public interface InvoiceRepository {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(Long id);
    Optional<Invoice> findByBookingId(String bookingId);
    void update(Invoice invoice);
}
