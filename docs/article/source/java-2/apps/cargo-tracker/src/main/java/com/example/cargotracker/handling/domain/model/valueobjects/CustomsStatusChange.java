package com.example.cargotracker.handling.domain.model.valueobjects;

import java.time.Instant;

/**
 * 通関状態の変更 1 回分（US29「変更履歴（日時・変更者・理由）が参照できる」）。
 *
 * <p><strong>監査ログとは別に永続化する。</strong> 監査ログはアプリのログであり、
 * 画面から読めない。受入基準は「申告詳細から<strong>参照できる</strong>」と言っている。
 *
 * <p>理由は履歴側が持つ。申告本体に持たせると<strong>更新のたびに上書きされ</strong>、
 * 「なぜ留置されたのか」が最後の 1 回しか残らない。
 *
 * @param from      変更前の状態
 * @param to        変更後の状態
 * @param reason    理由。<strong>必須</strong>
 * @param changedBy 変更者
 * @param changedAt 変更日時
 */
public record CustomsStatusChange(
        CustomsStatus from, CustomsStatus to, String reason,
        String changedBy, Instant changedAt) {

    public CustomsStatusChange {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("理由は必須です");
        }
    }
}
