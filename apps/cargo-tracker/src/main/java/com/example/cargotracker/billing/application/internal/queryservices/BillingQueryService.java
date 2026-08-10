package com.example.cargotracker.billing.application.internal.queryservices;

import java.util.List;
import java.util.Optional;

/**
 * 請求の読み取り（US21 / US22。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface BillingQueryService {

    /**
     * 請求書がまだ無い引取済みの貨物（請求対象一覧）。
     *
     * <p><strong>「気づく手段」だけでは仕事は進まない。</strong> ここから
     * 1 件ずつ料金算出へ進める。
     */
    List<PendingCargoView> findPendingCargo();

    /** 請求対象の件数（ダッシュボードのカード。ADR-014）。 */
    int countPendingCargo();

    /**
     * 支払期限を過ぎた請求書の件数（US23 の受入基準 5）。
     *
     * <p><strong>一覧を組み立てずに数える</strong>（IT13 レビュー C4）。
     * ダッシュボードは表示のたびにこれを呼ぶ。
     */
    int countOverdueInvoices();

    /** 精算書の一覧。 */
    List<InvoiceView> findInvoices(String chargeStatus);

    /** 精算書 1 件。 */
    Optional<InvoiceView> findInvoice(String invoiceNumber);

    /** 予約に紐づく精算書（<strong>二重請求の判定と導線に使う</strong>）。 */
    Optional<InvoiceView> findInvoiceByBookingId(String bookingId);
}
