package com.example.shared.events;

import java.time.LocalDateTime;

/**
 * 配送完了イベント（US16、cross-service / ADR-0009）。
 *
 * <p>trackingms の {@code TrackingActivity} 集約が {@code DELIVERED} 状態に遷移したときに
 * shared モジュールのイベントとして発行する。IT7 Billing で {@code Invoice} 集約の精算開始トリガー
 * （{@code CalculateInvoiceCommand}）として購読される（domain-model.md / iteration_plan-7）。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、trackingms / bookingms / billingms が
 * 同一 FQCN でシリアライズ・デシリアライズできるようにする。</p>
 *
 * @param trackingNumber 追跡番号
 * @param bookingId      予約識別子（Billing の集約識別子に使用）
 * @param deliveredAt    配送完了時刻（最終港 CLAIM 発生時刻）
 */
public record CargoDeliveredEvent(
        String trackingNumber,
        String bookingId,
        LocalDateTime deliveredAt
) {
}
