package com.example.cargotracker.billing.domain.repository;

import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import java.util.Optional;

/**
 * 精算書の出力ポート（US21 / US22）。実装はインフラ層に置く（DIP）。
 */
public interface InvoiceRepository {

    /** 新しい精算書を保存し、採番された ID を返す。 */
    long save(Invoice invoice);

    /**
     * 精算書を更新する（楽観的ロック付き）。
     *
     * @return 更新できたか。<strong>0 件は「別の担当者が先に確定した」ことを表す</strong>
     */
    boolean update(Invoice invoice);

    Optional<Invoice> findByInvoiceId(InvoiceId invoiceId);

    /**
     * 予約に紐づく精算書。
     *
     * <p><strong>二重請求の判定に使う。</strong> DB の一意制約でも防いでいるが、
     * 制約に頼ると画面には 500 が出る（業務の言葉で拒む）。
     */
    Optional<Invoice> findByBookingId(BillingBookingId bookingId);

    /** 次の精算書番号を採番する。 */
    InvoiceId nextInvoiceId();
}
