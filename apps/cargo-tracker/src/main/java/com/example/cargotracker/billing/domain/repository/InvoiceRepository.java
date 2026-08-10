package com.example.cargotracker.billing.domain.repository;

import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceType;
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
     * 予約と種別に紐づく精算書。
     *
     * <p><strong>二重請求の判定に使う。</strong> DB の一意制約でも防いでいるが、
     * 制約に頼ると画面には 500 が出る（業務の言葉で拒む）。
     *
     * <p><strong>種別を渡させる</strong>（US30）。1 つの予約に輸送料金と
     * キャンセル料が並びうるため、予約だけでは 1 枚に定まらない。
     */
    Optional<Invoice> findByBookingId(BillingBookingId bookingId, InvoiceType invoiceType);

    /** 次の精算書番号を採番する。 */
    InvoiceId nextInvoiceId();

    /**
     * 支払いの状態で引く（US23。督促の判定と一覧）。
     *
     * @param paymentStatus 支払いの状態
     * @return 発行済みのものだけ（<strong>未発行は支払いの話が始まっていない</strong>）
     */
    java.util.List<Invoice> findByPaymentStatus(String paymentStatus);

    /**
     * 支払期限を過ぎた未入金の請求書（US23。督促の判定）。
     *
     * <p><strong>候補を DB 側で絞る。</strong> 未入金の全件を集約に復元すると、
     * 画面を開くたびに未入金の件数に比例した読み込みが走る。
     *
     * <p><strong>期限の判断そのものは集約が行う</strong>（{@code markOverdue}）。
     * SQL は「その日より前」で候補を絞るだけであり、
     * <strong>当日を含めるかどうかの規則を 2 か所に書かない</strong>。
     *
     * @param today 業務のタイムゾーンでの今日
     */
    java.util.List<Invoice> findOverdueCandidates(java.time.LocalDate today);

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
