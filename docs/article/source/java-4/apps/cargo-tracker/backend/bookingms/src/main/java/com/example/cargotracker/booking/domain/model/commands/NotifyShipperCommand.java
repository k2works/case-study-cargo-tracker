package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 確定した経路を荷主へ通知した記録を残す（UC10 / US12）。
 *
 * <p><b>送信基盤はスコープ外である</b>（domain-model.md）。通知は現行の手作業
 * （電話・メール）で行い、システムは通知した事実だけを記録する。<b>それでも
 * 記録は業務の守りとして働く</b>——「通知済み」になった予約だけが確定へ進める。</p>
 *
 * <p>宛先と要約を載せる。<b>要約を残さないと「何を伝えたか」が分からない</b>ので、
 * 荷主から「聞いていない」と言われたときに突き合わせられない。</p>
 */
public record NotifyShipperCommand(
        @TargetEntityId String bookingId,
        String recipientEmail,
        String summary,
        String notifiedBy) {
}
