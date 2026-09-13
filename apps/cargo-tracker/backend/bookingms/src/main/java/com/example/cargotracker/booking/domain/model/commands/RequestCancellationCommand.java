package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 予約のキャンセルを申し出る（UC22 / US30 §受入基準 1・2・3）。
 *
 * <p><b>入口は 1 つ。</b> 輸送開始前は即座にキャンセルになり、輸送中は申請になる
 * ——どちらになるかは<b>集約が状態から決める</b>。画面が出し分けると、同じ判断が
 * 2 か所に住むことになり、片方だけが正しいまま残る。</p>
 *
 * @param reason 理由。<b>必須</b>（§受入基準 3）——あとから「なぜ止めたか」を
 *     読む人のために書く
 */
public record RequestCancellationCommand(
        @TargetEntityId String bookingId,
        String requestId,
        String reason,
        String requestedBy) {
}
