package com.example.bookingms.domain.model.aggregates;

import java.time.Instant;

/**
 * 荷主へ経路を通知した記録（US12-4・[ADR-021] 決定 1）。
 *
 * <p>「いつ・誰が」で 1 組の意味を持つ（[ADR-012]）。片方だけでは業務上の意味が無い——
 * 日時だけでは誰に聞けばよいか分からず、担当者だけではいつの話か分からない。
 *
 * <p><strong>持つのは最新の 1 件だけである</strong>（[ADR-021] 決定 2）。何回・いつ送ったかの
 * 履歴は列では表せず、別テーブルが要る。通知の実体（メール送信）が US19 で入るまで、
 * 履歴を持っても送った証拠にはならない。<strong>履歴を持たないことは決定であり、
 * 実装漏れではない。</strong>
 *
 * @param notifiedAt 通知した日時
 * @param notifiedBy 通知した担当者の利用者 ID
 */
public record RouteNotification(Instant notifiedAt, String notifiedBy) {

    /** 新しく記録する。ここで検査する。 */
    public static RouteNotification of(Instant notifiedAt, String notifiedBy) {
        if (notifiedAt == null) {
            throw new IllegalArgumentException("通知した日時は必須です");
        }
        if (notifiedBy == null || notifiedBy.isBlank()) {
            throw new IllegalArgumentException("通知した担当者は必須です");
        }
        return new RouteNotification(notifiedAt, notifiedBy);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない（[ADR-012]）。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。守るのは
     * 新しく受け入れるときだけでよい。
     *
     * <p>通知していない予約では両方 {@code null} になる。そのときは記録が無いことを表す
     * {@code null} を返す——空の記録を作ると「通知したが日時が分からない」と区別できない。
     */
    public static RouteNotification restore(Instant notifiedAt, String notifiedBy) {
        return notifiedAt == null && notifiedBy == null
                ? null
                : new RouteNotification(notifiedAt, notifiedBy);
    }
}
