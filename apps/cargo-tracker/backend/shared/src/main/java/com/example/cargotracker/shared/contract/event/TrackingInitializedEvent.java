package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡を開始した（US14）。trackingms → bookingms。<b>契約イベント</b>。
 *
 * <p>これを受けて {@code BookingReactionHandler} が連鎖を終える（{@code process_state}
 * を {@code COMPLETED} にする）。連鎖の最後の段が「届いたこと」を知る唯一の手立てで、
 * これが来ないまま 24 時間経った行が「止まった連鎖」である。</p>
 *
 * <p><b>コマンドで届いた値をここに載せ直す。</b> bookingms が読むのは
 * {@code trackingNumber} と {@code bookingId} だけだが、<b>trackingms 自身の投影は
 * このイベントからしか作れない</b>（投影はコマンドを読まない）。載せないと、
 * 追跡の一覧に出発地も目的地も出せず、荷役（IT9）が旅程を照合できない。</p>
 *
 * <p><b>状態を載せない。</b> 追跡を始めた直後がどの状態か（{@code NOT_RECEIVED}）は
 * trackingms の {@code TransportStatus} の話で、bookingms には別の意味の状態がある。
 * 同じ名前でも BC ごとに値と意味が違うので、列挙型も状態名も契約に出さない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると trackingms の集約は空のまま
 * 復元され、「二重に開始しない」守りが素通りする。</p>
 */
public record TrackingInitializedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        String originUnLocode,
        String destinationUnLocode,
        String cargoType,
        List<Leg> legs,
        Instant initializedAt) {

    public TrackingInitializedEvent {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /** 予定の旅程の 1 区間。積む順。荷役（IT9）が予定と実績を照合する材料。 */
    public record Leg(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadTime,
            Instant unloadTime) {
    }
}
