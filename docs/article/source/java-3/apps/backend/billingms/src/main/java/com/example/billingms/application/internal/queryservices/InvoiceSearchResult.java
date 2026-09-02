package com.example.billingms.application.internal.queryservices;

import com.example.billingms.domain.model.aggregates.Invoice;
import com.example.billingms.domain.model.valueobjects.Money;
import java.util.List;

/**
 * 請求書の検索結果（US38）。
 *
 * <p><strong>件数と合計を一覧と一緒に返す。</strong>別々に引くと、引いた瞬間の
 * 違いで「12 件あります」と出るのに開くと 3 件、という形になる。
 *
 * @param invoices 見つかった請求書（新しい順）
 * @param count 同じ条件に合う総件数
 * @param total 同じ条件に合う<strong>取り消し済みを除いた</strong>合計金額
 */
public record InvoiceSearchResult(List<Invoice> invoices, long count, Money total) {

    public InvoiceSearchResult {
        invoices = invoices == null ? List.of() : List.copyOf(invoices);
    }
}
