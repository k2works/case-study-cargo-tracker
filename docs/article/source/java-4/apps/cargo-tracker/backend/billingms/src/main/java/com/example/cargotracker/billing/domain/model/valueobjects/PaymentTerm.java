package com.example.cargotracker.billing.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * 支払条件（不変条件 3・4）。
 *
 * <p><b>Java 側の判定をここ 1 か所に置く。</b> 集約（{@code Invoice#overdue}）と
 * 一覧の行（{@code InvoiceQueryHandler#toSummary}）の両方が読む——別々に書くと、
 * 片方だけが正しくてもう片方が誤りを素通りさせる。IT10 で「判定をテスト側に
 * 書き直さない」と決めたのと同じ理由で、<b>本番の中でも書き直さない</b>。</p>
 *
 * <p><b>絞り込みだけは SQL にも同じ規則がある</b>（{@code InvoiceMapper#findOverdue}
 * の {@code due_on < today}）。全件を読んで Java で数えると、上限の打ち切りで
 * 未払いが漏れるので、そこは寄せられない。<b>2 か所あることを隠さず</b>、
 * 境界（期限当日と翌日）を SQL 側でも別に固定する
 * （{@code InvoiceProjectionIT#doesNotListTheInvoiceOnItsDueDate}）。</p>
 */
public final class PaymentTerm {

    /** 支払サイト（不変条件 3）。<b>業務が決める数字</b>だが、この版では固定でよい。 */
    public static final int DAYS = 30;

    private PaymentTerm() {
    }

    /** 発行日から支払期限を決める。 */
    public static LocalDate dueOn(LocalDate issuedOn) {
        return issuedOn.plusDays(DAYS);
    }

    /**
     * 支払期限を過ぎているか。
     *
     * <p><b>列に持たない。</b> 持つと、日付が変わるたびに全件を書き換えることに
     * なり、書き換えそこねた行が静かに未払いから漏れる。</p>
     *
     * <p><b>期限当日は超過ではない。</b> 当日中に入金されることはふつうにある。</p>
     *
     * <p><b>{@code today} は業務タイムゾーンで決める</b>（呼ぶ側の責任）。UTC で
     * 判断すると、時差の分だけ 1 日早く督促が飛ぶ時間帯ができる。</p>
     *
     * <p>未発行・入金済・取消に「期限を過ぎた」は無い。</p>
     */
    public static boolean overdue(BillingStatus status, LocalDate dueOn, LocalDate today) {
        if (status != BillingStatus.INVOICED || dueOn == null || today == null) {
            return false;
        }
        return today.isAfter(dueOn);
    }
}
