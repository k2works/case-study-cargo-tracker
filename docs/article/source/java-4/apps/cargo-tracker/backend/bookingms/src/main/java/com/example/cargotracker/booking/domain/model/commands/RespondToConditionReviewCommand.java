package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷主と協議した結果を経路設計者へ返す（UC08 / US10 §受入基準 4 の対）。
 *
 * <p>経路設計者は「この条件では組めない」と差し戻せる
 * （{@link RequestConditionReviewCommand}）。<b>その逆向きが無いと、営業は
 * 協議を終えても伝える手段を持たない</b>——差し戻しは営業のダッシュボードに
 * 出たままになり、経路設計者は「返事が来たか」を画面から読めない（IT6 レビュー）。</p>
 *
 * <p><b>状態は動かさない</b>（ADR-0009 決定 1）。差し戻しと同じく記録で表す。
 * 条件を実際に変えるのは経路設計者で、営業の画面からは直せない
 * （マニュアル 09 章）。ここで返すのは<b>協議の結果</b>である。</p>
 *
 * @param response 荷主と何が決まったか。経路設計者が条件を直すときに読む
 */
public record RespondToConditionReviewCommand(
        @TargetEntityId String bookingId,
        String response,
        String respondedBy) {
}
