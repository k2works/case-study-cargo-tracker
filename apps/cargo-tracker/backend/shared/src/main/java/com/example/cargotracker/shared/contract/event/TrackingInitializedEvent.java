package com.example.cargotracker.shared.contract.event;

import java.math.BigDecimal;
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
 * <p><b>{@code shipperId} を載せる。</b> 投影はコマンドを読まないので、コマンドに
 * 載せただけでは荷主向けの追跡一覧（US18）が作れない。<b>イベントは購読側の投影が
 * 作れる分を運ぶ</b>——受け側の列を先に並べて確かめる（IT7 の教訓）。</p>
 *
 * <p><b>{@code weightKg} を載せる（IT13）。</b> 請求（billingms）は実際に運んだ重量で
 * 基本料金を数えるが、<b>重量を運ぶ契約が 1 つも無かった</b>。{@code CargoDeliveredEvent}
 * は追跡番号・予約・引渡時刻だけ、このイベントは区間と貨物種別までで、重量はどこにも
 * 無い。<b>イベントは購読側の投影が作れる分を運ぶ</b>ので、ここに足す。</p>
 *
 * <p><b>{@code null} を許す。</b> 足す前に積まれたイベントには入っていない——契約は
 * 追記専用で、過去のイベントは書き換えられない。読めなくなればその貨物は復元できなく
 * なるので、既定値（{@code null}）で読めるようにし、<b>重量が分からない貨物は請求を
 * 作らずに要確認へ出す</b>。足りない重量で安い請求を黙って出さない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると trackingms の集約は空のまま
 * 復元され、「二重に開始しない」守りが素通りする。</p>
 */
public record TrackingInitializedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        String shipperId,
        String originUnLocode,
        String destinationUnLocode,
        String cargoType,
        BigDecimal weightKg,
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
