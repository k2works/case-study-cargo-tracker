package com.example.cargotracker.billingms.application.query;

import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 請求クエリサービス（US21 Query Side）。
 */
@Service
@Transactional(readOnly = true)
public class BillingQueryService {

    private final InvoiceMapper invoiceMapper;

    public BillingQueryService(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    public List<InvoiceRecord> findAll() {
        return invoiceMapper.findAll();
    }

    public Optional<InvoiceRecord> findById(String invoiceId) {
        return invoiceMapper.findById(invoiceId);
    }

    public List<InvoiceRecord> findOverdue() {
        return invoiceMapper.findByStatus("OVERDUE");
    }

    public boolean exists(String invoiceId) {
        return invoiceMapper.findById(invoiceId).isPresent();
    }
}
