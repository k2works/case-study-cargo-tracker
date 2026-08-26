package com.example.billingms.application.port;

import com.example.billingms.domain.model.Invoice;
import java.util.List;
import java.util.Optional;

/** 精算書の永続化（出力ポート）。 */
public interface InvoiceRepository {

    void save(Invoice invoice);

    Optional<Invoice> findById(String invoiceId);

    List<Invoice> findAll();

    /**
     * その予約に精算書が発行済みか（決定 4）。
     *
     * <p><strong>二重請求を防ぐ。</strong>DB の UNIQUE 制約でも守るが、制約だけだと
     * 画面に 500 が出る——利用者には「なぜ断られたか」が伝わらない。
     */
    boolean existsForBooking(String bookingId);

    /**
     * 入金の確認を書き込む（受入基準 23-3・[ADR-028] 決定 2）。
     *
     * <p><strong>請求書の金額は書き換えない。</strong>入金は別表に足し、
     * 請求書側は支払いの状態だけを動かす。
     */
    void confirmPayment(Invoice invoice);

    /**
     * 取り消しを書き込む（赤伝・決定 3）。
     *
     * <p><strong>行は消さない。</strong>取り消したことと理由を足す。
     */
    void revoke(Invoice invoice);
}
