package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡番号の発行を取り消す（US14 の補償）。
 *
 * <p>trackingms へ追跡開始が届かないまま再試行の上限を超えたときに送る。
 * <b>予約は {@code CONFIRMED} に留まる</b>——キャンセルではないので、経路設計者が
 * もう一度発行できる状態に戻すだけである。</p>
 *
 * <p><b>取り消したことを記録に残す。</b> 状態を黙って戻すと、追跡番号が画面から
 * 消えた理由を誰も説明できない。</p>
 *
 * @param reason なぜ取り消したか。要確認一覧に出る
 */
public record RevertTrackingNumberCommand(
        @TargetEntityId String bookingId,
        String reason) {
}
