package com.example.billingms.domain.repository;

import com.example.billingms.domain.model.aggregates.Invoice;
import java.util.List;
import java.util.Optional;

/** 精算書の永続化（出力ポート）。 */
public interface InvoiceRepository {

    void save(Invoice invoice);

    Optional<Invoice> findById(String invoiceId);

    List<Invoice> findAll();

    /**
     * 条件に合う請求書を新しい順に返す（US38）。
     *
     * <p><strong>絞り込みは SQL に降ろす。</strong>読んでから絞ると、請求書が
     * 増えた月ほど窓の外に落ちる——件数だけで壊れる形である。
     */
    List<Invoice> search(com.example.billingms.domain.model.valueobjects.InvoiceSearchCriteria
            criteria, int limit);

    /** 同じ条件に合う総件数。**一覧と同じ条件を使う**。 */
    long count(com.example.billingms.domain.model.valueobjects.InvoiceSearchCriteria criteria);

    /**
     * 同じ条件に合う合計金額。
     *
     * <p><strong>取り消し済みは入れない。</strong>合計は締めの数字としてそのまま
     * 使われる——赤伝を含めると、誤りに気づく手段が無いまま経理の判断に入る。
     */
    com.example.billingms.domain.model.valueobjects.Money total(
            com.example.billingms.domain.model.valueobjects.InvoiceSearchCriteria criteria);

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
