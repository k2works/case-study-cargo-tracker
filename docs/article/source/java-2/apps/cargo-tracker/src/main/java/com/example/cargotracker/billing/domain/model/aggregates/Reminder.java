package com.example.cargotracker.billing.domain.model.aggregates;

import java.time.Instant;

/**
 * 督促の記録（IT14 レビュー C3）。
 *
 * <p><strong>「気づく手段」は次の行動へ繋ぐ。</strong> 支払期限を過ぎた請求書を
 * 数えるところまでは US23 で作った。<strong>そこから何をしたかが残らない</strong>と、
 * 二重に催促するか、逆に誰も連絡しないまま月をまたぐ。
 *
 * <p><strong>伝えた内容は空でよい。</strong> 電話で伝えたことだけが事実の場合がある。
 * <strong>いつ・誰が</strong>は空にできない — それが記録の本体だからである。
 *
 * @param remindedAt 督促した日時
 * @param remindedBy 督促した人
 * @param note       伝えた内容。<strong>無ければ {@code null}</strong>
 */
public record Reminder(Instant remindedAt, String remindedBy, String note) {

    /** 伝えた内容の上限（DB の列と揃える）。 */
    public static final int NOTE_MAX_LENGTH = 500;

    public Reminder {
        if (remindedAt == null) {
            throw new IllegalArgumentException("督促した日時は必須です");
        }
        if (remindedBy == null || remindedBy.isBlank()) {
            throw new IllegalArgumentException("督促した人は必須です");
        }
        note = note == null || note.isBlank() ? null : note.strip();
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "伝えた内容は %d 文字までです".formatted(NOTE_MAX_LENGTH));
        }
    }

    /** 伝えた内容があるか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean hasNote() {
        return note != null;
    }
}
