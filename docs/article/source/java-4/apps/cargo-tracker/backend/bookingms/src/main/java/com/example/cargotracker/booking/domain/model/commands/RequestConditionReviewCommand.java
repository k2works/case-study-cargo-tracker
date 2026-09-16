package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 条件では組めないことを営業へ差し戻す（UC08 / US10 §受入基準 4）。
 *
 * <p><b>状態は動かさない</b>（ADR-0009）。戻すと「一度も設計していない予約」と
 * 区別が付かなくなり、経路設計作業一覧（S30）と誤配の扱いにも波及する。</p>
 *
 * @param reason 何が足りないのか。営業が荷主と協議するときに読む
 */
public record RequestConditionReviewCommand(
        @TargetEntityId String bookingId,
        String reason,
        String requestedBy) {
}
