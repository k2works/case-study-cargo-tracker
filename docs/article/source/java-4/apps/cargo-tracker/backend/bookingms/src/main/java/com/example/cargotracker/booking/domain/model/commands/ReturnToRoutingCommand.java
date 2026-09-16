package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 通知した経路を経路設計へ戻す（UC08 / US12）。
 *
 * <p>荷主が経路の変更を求めたときに営業が使う。<b>{@code RequestRoutingCommand} を
 * 再利用しない</b>——再利用すると「引き渡した」と「通知後に戻した」がイベント履歴で
 * 区別できなくなり、{@code routing_requested_at} の書き手も 2 つになる。</p>
 *
 * @param reason 荷主が何を求めているのか。経路設計者が読む
 */
public record ReturnToRoutingCommand(
        @TargetEntityId String bookingId,
        String reason,
        String returnedBy) {
}
