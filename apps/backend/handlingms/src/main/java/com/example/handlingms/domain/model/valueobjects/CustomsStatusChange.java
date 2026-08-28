package com.example.handlingms.domain.model.valueobjects;

import java.time.Instant;

/**
 * 通関状態の変更 1 件（US29-8）。監査の履歴であり、**追記しかしない**。
 *
 * <p><strong>{@code fromStatus} も必ず値を持つ</strong>（`data-model.md` の
 * `from_status NOT NULL`）。登録そのものも 1 行目として残すため、初回は
 * {@code PENDING → PENDING} になる。空にすると「登録なのか、前の状態が
 * 分からないのか」が読めない。
 *
 * <p><strong>理由は必須である</strong>（US29-2）。空で通すと、監査の履歴が
 * 「誰かが変えた」だけになる。
 *
 * @param fromStatus 変更前の状態
 * @param toStatus 変更後の状態
 * @param changedBy 変更した利用者
 * @param changedAt 変更日時
 * @param reason 変更の理由
 */
public record CustomsStatusChange(CustomsStatus fromStatus, CustomsStatus toStatus,
        String changedBy, Instant changedAt, String reason) {

    public CustomsStatusChange {
        if (fromStatus == null || toStatus == null) {
            throw new IllegalArgumentException("通関状態は必須です");
        }
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("変更者は必須です");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("変更日時は必須です");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("変更の理由は必須です");
        }
    }
}
