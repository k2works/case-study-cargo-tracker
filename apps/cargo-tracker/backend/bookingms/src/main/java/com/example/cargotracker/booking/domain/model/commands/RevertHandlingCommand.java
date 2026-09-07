package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 取り消された荷役の分を予約から戻す（不変条件 13）。
 *
 * <p><b>誤配の原因が取り消された荷役だけなら、経路設計の状態も戻す。</b>
 * 取り消したのに作業一覧へ残り続けると、経路設計者は起きていない誤配を
 * 組み直そうとする。</p>
 */
public record RevertHandlingCommand(
        @TargetEntityId String bookingId,
        String activityId,
        String reason) {
}
