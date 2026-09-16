package com.example.cargotracker.tracking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷主へ知らせた事実を記録する（UC16 / US19 §受入基準 3）。
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。通知は現行の手作業
 * （電話・メール）で行い、システムは<b>いつ・どうやって・何を伝えたか</b>だけを
 * 記録する。荷主から「聞いていない」と言われたときに突き合わせる 材料 になる。</p>
 *
 * @param means 伝えた手段（電話・メールなど）
 */
public record NotifyShipperOfExceptionCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        String means,
        String summary,
        String notifiedBy) {
}
