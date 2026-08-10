package com.example.cargotracker.billing.application.internal.queryservices;

import java.time.Instant;

/**
 * 画面に出す督促の記録（IT14 レビュー C3）。
 *
 * @param note 伝えた内容。<strong>無ければ {@code null}</strong>
 */
public record ReminderView(Instant remindedAt, String remindedBy, String note) {

    /** 伝えた内容があるか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean hasNote() {
        return note != null;
    }
}
