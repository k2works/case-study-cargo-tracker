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

    /**
     * 精算（発行・期限超過）を保存する（US23）。
     *
     * <p><strong>金額の更新と分ける。</strong> 金額は確定前にしか動かず、
     * 精算は確定後にしか起きない。1 つのメソッドにすると、
     * <strong>どちらの条件で守るのかが決まらない</strong>。
     *
     * @return 更新できたなら {@code true}（他の更新が先行していれば {@code false}）
     */
    boolean updateSettlement(Invoice invoice);

    /**
     * 入金を記録して精算を保存する（US23）。
     *
     * <p><strong>入金の記録と状態の更新はひと組である。</strong> 分けると、
     * 入金だけ残って状態が未入金のままの行を作れてしまう。
     *
     * @return 更新できたなら {@code true}
     */
    boolean savePayment(Invoice invoice);
}
