package com.example.cargotracker.billing.domain.repository;

import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.aggregates.Reminder;
import java.util.List;

/**
 * 督促の記録の出力ポート（IT14 レビュー C3）。実装はインフラ層に置く（DIP）。
 */
public interface ReminderRepository {

    /**
     * 督促を記録する。
     *
     * @return 記録できたなら {@code true}（<strong>請求書が無ければ {@code false}</strong>）
     */
    boolean save(InvoiceId invoiceId, Reminder reminder);

    /** 督促の記録（<strong>新しい順</strong>）。 */
    List<Reminder> findByInvoiceId(InvoiceId invoiceId);
}
