package com.example.cargotracker.booking.domain.model.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 仮受付の予約情報を修正した（UC03・UC04 / US32）。
 *
 * <p><b>このイベントそのものが修正の履歴である</b>（US32 §受入基準 4）。変更内容の
 * 履歴テーブルは作らない。同じ事実を 2 か所で持つと必ずずれる。投影には最終更新の
 * 2 列（{@code updated_at} / {@code updated_by}）だけを置く。</p>
 *
 * <p>契約イベントではない（bookingms の内側だけで読む）。{@code CargoBookedEvent} と
 * 同じ形にしているのは、投影が「受付」と「修正」で同じ列を書くためである。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「仮受付だけ修正できる」という守りが素通りする。</p>
 */
public record CargoSpecificationUpdatedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        int quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC,
        String updatedBy,
        // 直した時刻。投影が現在時刻で決めない。決めると、投影を読み直すたびに
        // 「いつ直したか」が動き、読み直した日時が最終更新として画面に出る。
        java.time.Instant updatedAt) {
}
