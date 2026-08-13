package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 引取の取り消しが承認された（US36）。
 *
 * <p>引取は輸送の終点であり、<strong>誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる</strong>。取り消しが承認されると、予約は配送完了から
 * 輸送中へ、追跡は引取完了から引取の直前の状態へ戻る。
 *
 * <p><strong>Handling から他 BC を呼ばない</strong>（ADR-012）。運ぶのは起きた事実で
 * あり命令ではない。どう解釈するかは購読側が決める（ADR-009 の結果整合）。
 *
 * <p><strong>元の記録は消えない。</strong> 取り消されたのは「引き渡した」という
 * 状態であって、登録された事実ではない。
 *
 * @param bookingId      予約 ID
 * @param trackingNumber 追跡番号
 * @param reason         取り消しの理由。<strong>荷主への通知にそのまま載る</strong>
 * @param approvedBy     承認した追跡管理者
 * @param approvedAt     承認日時
 */
public record ClaimCancelledEvent(
        UUID bookingId,
        String trackingNumber,
        String reason,
        String approvedBy,
        Instant approvedAt) {
}
