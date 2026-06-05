package com.example.billingms.application;

import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.domain.projections.Payment;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import com.example.billingms.infrastructure.repositories.mybatis.PaymentMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
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
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    public InvoiceQueryService(InvoiceSummaryMapper summaryMapper,
                               InvoiceLineMapper lineMapper,
                               PaymentMapper paymentMapper,
                               Clock clock) {
        this.summaryMapper = summaryMapper;
        this.lineMapper = lineMapper;
        this.paymentMapper = paymentMapper;
        this.clock = clock;
    }

    public InvoiceSummary findByInvoiceId(String invoiceId) {
        return summaryMapper.findByInvoiceId(invoiceId);
    }

    public List<InvoiceLine> findLinesByInvoiceId(String invoiceId) {
        return lineMapper.findByInvoiceId(invoiceId);
    }

    /** invoiceId 単位の入金履歴（時系列、US23 S23 詳細画面の payment 履歴セクション）。 */
    public List<Payment> findPaymentsByInvoiceId(String invoiceId) {
        return paymentMapper.findByInvoiceId(invoiceId);
    }

    public List<InvoiceSummary> findAll(int offset, int limit) {
        return summaryMapper.findAll(offset, limit);
    }

    public long count() {
        return summaryMapper.count();
    }

    /** billing_status フィルタ付き一覧（US23 / T4.3 / S22 フィルタ）。 */
    public List<InvoiceSummary> findByStatus(String billingStatus, int offset, int limit) {
        return summaryMapper.findByStatus(billingStatus, offset, limit);
    }

    public long countByStatus(String billingStatus) {
        return summaryMapper.countByStatus(billingStatus);
    }

    /** 督促対象（INVOICED かつ payment_due 超過）の一覧（US23 / T4.3, T4.6、S25 督促一覧）。 */
    public List<InvoiceSummary> findOverdueCandidates() {
        return summaryMapper.findOverdueCandidates(LocalDate.now(clock));
    }
}
