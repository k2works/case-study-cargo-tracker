package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 貨物に例外が起きた（US19 / US20）。
 *
 * <p>Tracking Context が発行し、Booking Context が購読して<strong>荷主への通知として
 * 記録する</strong>（受入基準「荷主に発生の通知が送信される」）。ADR-006 により
 * 外部へは送らない。通知の実体は記録である（US12 で確立）。
 *
 * <p><strong>Tracking から Booking を呼ばない</strong>（ADR-012）。呼ぶと消したはずの
 * Booking ⇄ Tracking の循環が戻る。起きた事実だけを伝え、荷主に何と伝えるかは
 * Booking が決める（ADR-009）。{@link CargoStatusUpdatedEvent} と同じ形である。
 *
 * @param bookingId          予約 ID
 * @param trackingNumber     追跡番号
 * @param occurrence 何が・いつ・どこで起きたか
 * @param escalated          管理職へのエスカレーションが要るか（US20）
 * @param raisedBy           起票した人
 */
public record CargoExceptionRaisedEvent(
        UUID bookingId,
        String trackingNumber,
        Occurrence occurrence,
        boolean escalated,
        String raisedBy) {

    /**
     * 何が・いつ・どこで起きたか。
     *
     * @param typeLabel        例外種別の表示名
     * @param at               発生日時
     */
    public record Occurrence(
            String typeLabel, Instant at, String locationUnlocode, String description) { }

    // --- 購読側が使う名前（委譲するアクセサ）---

    /** @return 例外種別の表示名 */
    public String exceptionTypeLabel() {
        return occurrence.typeLabel();
    }

    /** @return 発生日時 */
    public Instant occurredAt() {
        return occurrence.at();
    }

    /** @return 発生場所 */
    public String locationUnlocode() {
        return occurrence.locationUnlocode();
    }

    /** @return 状況 */
    public String description() {
        return occurrence.description();
    }

}
