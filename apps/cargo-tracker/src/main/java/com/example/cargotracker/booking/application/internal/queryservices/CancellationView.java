package com.example.cargotracker.booking.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 画面に出すキャンセル申請（US30）。
 *
 * <p><strong>候補は戻り値をそのまま出す</strong>（ADR-021 の T1）。
 * 画面で組み立て直すと、集約が守っている「候補の中からしか選べない」と
 * 見えている選択肢がずれる。
 *
 * @param candidates      陸揚げ地の候補（現在地の港とまだ着いていない寄港地）
 * @param currentUnlocode いまの場所。<strong>読めなければ {@code null}</strong>
 * @param dischargeUnlocode 決まった陸揚げ地。<strong>承認前・却下では {@code null}</strong>
 * @param decisionReason  却下の理由。<strong>承認・未決では {@code null}</strong>
 */
public record CancellationView(
        long id,
        String bookingId,
        String trackingNumber,
        String shipperName,
        String origin,
        String destination,
        String reason,
        String requestedBy,
        Instant requestedAt,
        String statusLabel,
        String statusBadge,
        boolean pending,
        BigDecimal feePercent,
        String currentUnlocode,
        List<String> candidates,
        String dischargeUnlocode,
        String decidedBy,
        Instant decidedAt,
        String decisionReason) {

    /** <strong>候補を写して持つ。</strong> 外から差し替えられると、
     * 画面に出す選択肢と承認が受け付ける値がずれる。 */
    public CancellationView {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** いまの場所が読めたか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean hasCurrentLocation() {
        return currentUnlocode != null;
    }

    /** 却下の理由があるか。 */
    public boolean hasDecisionReason() {
        return decisionReason != null;
    }
}
