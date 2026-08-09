package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 訂正・取り消し申請の 1 行（US36）。
 *
 * <p><strong>誰が何を、どの貨物について申請したのかを 1 行で読めるようにする。</strong>
 * 承認するかどうかは、対象の貨物が分からなければ決められない。
 *
 * @param id             申請 ID
 * @param trackingNumber 対象の追跡番号。**承認者が手にしているのはこれである**
 * @param typeLabel      種別（訂正・取り消し）の表示名
 * @param reason         申請の理由。<strong>これが承認の判断材料である</strong>
 * @param requestedBy    申請者。<strong>本人は承認できない</strong>
 * @param requestedAt    申請日時
 * @param statusLabel    状態の表示名
 * @param statusBadge    状態のバッジ（正典は {@code CorrectionStatus}）
 * @param decidedBy      決定した追跡管理者。未決なら {@code null}
 * @param decidedAt      決定日時。未決なら {@code null}
 * @param decisionReason 却下の理由。<strong>申請者が次に何をすればよいかの
 *                       唯一の情報である</strong>。承認・未決なら {@code null}
 */
public record CorrectionRequestView(
        long id,
        String trackingNumber,
        String typeLabel,
        String reason,
        String requestedBy,
        Instant requestedAt,
        String statusLabel,
        String statusBadge,
        String decidedBy,
        Instant decidedAt,
        String decisionReason) {

    /** まだ決まっていないか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isPending() {
        return decidedAt == null;
    }

    /** 却下の理由があるか。**却下されたのに理由が読めないと、次の手が打てない。** */
    public boolean hasDecisionReason() {
        return decisionReason != null && !decisionReason.isBlank();
    }
}
