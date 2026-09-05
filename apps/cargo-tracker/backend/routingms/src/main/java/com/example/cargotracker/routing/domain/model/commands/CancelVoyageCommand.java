package com.example.cargotracker.routing.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 航海をキャンセルする（UC19 / US24）。
 *
 * <p>運航中止・抜港などで、その航海がもう走らなくなったことを記録する。
 * <b>削除ではない。</b> 既にその航海で経路を組んだ貨物があるので、行を消すと
 * 何が起きたのかを追えなくなる。走らない事実を状態として残す。</p>
 *
 * <p>これが無い間、不変条件 5（キャンセル済みは更新できない）へ到達する手段は
 * イベントの直接適用だけだった（IT4 引き継ぎ 1）。守りだけがあって到達できない
 * 状態は、本番では一度も踏まれない。</p>
 */
public record CancelVoyageCommand(
        @TargetEntityId String voyageNumber,
        String reason,
        String cancelledBy) {
}
