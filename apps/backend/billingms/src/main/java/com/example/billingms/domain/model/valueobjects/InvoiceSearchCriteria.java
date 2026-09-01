package com.example.billingms.domain.model.valueobjects;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 請求書を探す条件（US38）。
 *
 * <p>月末の締めでは「その月に出した請求書の合計」と「特定の荷主の請求書」を
 * 繰り返し引く。<strong>条件はここ 1 つに集める</strong>——一覧・件数・合計で
 * 別々に組み立てると、片方だけ直したときに「12 件あります」と出るのに開くと
 * 3 件、という形になる。
 *
 * <p><strong>レコードにしない。</strong>「指定なし」を {@link Optional} で返したいが、
 * レコードのアクセサは項目の型を返さなければならない。呼ぶ側に null 検査を書かせると、
 * 書き忘れた 1 か所が「絞っていないつもりで全件」になる。
 */
public final class InvoiceSearchCriteria {

    private final String keyword;

    private final YearMonth issuedMonth;

    /**
     * <strong>空白は「指定なし」である。</strong>入力欄を消したつもりの空白で
     * 「何にも一致しない検索」になると、経理は「1 件も無い」と読む。
     */
    private InvoiceSearchCriteria(String keyword, YearMonth issuedMonth) {
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        this.issuedMonth = issuedMonth;
    }

    public static InvoiceSearchCriteria of(String keyword, YearMonth issuedMonth) {
        return new InvoiceSearchCriteria(keyword, issuedMonth);
    }

    /**
     * 画面から届いた文字列を読み取る。
     *
     * <p><strong>読めない月は断る。</strong>黙って「指定なし」に倒すと、打ち間違えた
     * 担当者には全件が返り、絞ったつもりの数字を締めに使うことになる。
     */
    public static InvoiceSearchCriteria parse(String keyword, String issuedMonth) {
        if (issuedMonth == null || issuedMonth.isBlank()) {
            return of(keyword, null);
        }
        try {
            return of(keyword, YearMonth.parse(issuedMonth.trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "発行月は yyyy-MM の形式で指定してください: " + issuedMonth, e);
        }
    }

    public Optional<String> keyword() {
        return Optional.ofNullable(keyword);
    }

    public Optional<YearMonth> issuedMonth() {
        return Optional.ofNullable(issuedMonth);
    }

    /** 絞り込みが 1 つも無いか。 */
    public boolean isEmpty() {
        return keyword == null && issuedMonth == null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InvoiceSearchCriteria that
                && java.util.Objects.equals(keyword, that.keyword)
                && java.util.Objects.equals(issuedMonth, that.issuedMonth);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(keyword, issuedMonth);
    }
}
