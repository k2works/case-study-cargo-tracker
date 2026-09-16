package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷主との協議の結果を経路設計者へ返した（UC08 / US10 §受入基準 4 の対）。
 *
 * <p><b>状態は動かさない</b>（ADR-0009 決定 1）。営業の受け皿からは消え、
 * 経路設計者の画面に「営業から返事が来た」と出る。</p>
 *
 * <p><b>差し戻しの記録は消さない。</b> 何を頼まれて何が決まったかが対で読めないと、
 * 経路設計者は条件をどう直せばよいのか分からない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「差し戻されていない予約には返せない」守りが素通りする。</p>
 */
public record ConditionReviewRespondedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String response,
        String respondedBy,
        Instant respondedAt) {
}
