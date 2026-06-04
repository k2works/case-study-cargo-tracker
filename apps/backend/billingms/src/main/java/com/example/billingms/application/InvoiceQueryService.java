package com.example.billingms.application;

import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 請求書照会サービス（US21 / US23 / IT7 タスク 2.5）。
 *
 * <p>Controller から Read Model（invoice / invoice_line）への参照口。</p>
 */
@Service
public class InvoiceQueryService {

    private final InvoiceSummaryMapper summaryMapper;
    private final InvoiceLineMapper lineMapper;

    public InvoiceQueryService(InvoiceSummaryMapper summaryMapper, InvoiceLineMapper lineMapper) {
        this.summaryMapper = summaryMapper;
        this.lineMapper = lineMapper;
    }

    public InvoiceSummary findByInvoiceId(String invoiceId) {
        return summaryMapper.findByInvoiceId(invoiceId);
    }

    public List<InvoiceLine> findLinesByInvoiceId(String invoiceId) {
        return lineMapper.findByInvoiceId(invoiceId);
    }

    public List<InvoiceSummary> findAll(int offset, int limit) {
        return summaryMapper.findAll(offset, limit);
    }

    public long count() {
        return summaryMapper.count();
    }
}
