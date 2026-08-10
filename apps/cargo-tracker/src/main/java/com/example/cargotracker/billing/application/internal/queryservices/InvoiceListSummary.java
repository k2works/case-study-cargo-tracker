package com.example.cargotracker.billing.application.internal.queryservices;

import java.math.BigDecimal;
import java.util.List;

/**
 * 請求書一覧の締め（IT13 レビュー C2）。
 *
 * <p>経理が月次で最初にすることは「<strong>今月いくら請求したか</strong>」の確認である。
 * 1 行ずつ電卓で足すのは締めの作業ではない。
 *
 * <p><strong>絞り込んだ結果を数える。</strong> 全件の合計を出しっぱなしにすると、
 * 確定分だけを見ているつもりで下書きを含んだ額を総勘定元帳と比べることになる。
 * <strong>いま画面に並んでいる行がそのまま母集団である</strong>ことが要である。
 *
 * @param count       件数
 * @param totalAmount 請求総額の合計
 */
public record InvoiceListSummary(int count, BigDecimal totalAmount) {

    /**
     * 一覧から締めを作る。
     *
     * <p><strong>0 件でも 0 円を返す。</strong> 空を {@code null} にすると、
     * 画面が「合計が無い」と「合計が 0」を区別できず出し分けが増える。
     */
    public static InvoiceListSummary of(List<InvoiceView> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return new InvoiceListSummary(0, BigDecimal.ZERO);
        }
        return new InvoiceListSummary(
                invoices.size(),
                invoices.stream()
                        .map(InvoiceView::totalAmount)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
