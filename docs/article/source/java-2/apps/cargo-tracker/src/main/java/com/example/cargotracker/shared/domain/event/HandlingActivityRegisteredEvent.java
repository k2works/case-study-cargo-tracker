package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 荷役作業が登録された（US15）。
 *
 * <p>Handling Context が発行し、Tracking Context と Booking Context が購読する。
 * <strong>購読側は互いを知らない。</strong> 追跡は輸送状態を進め、予約は誤配の反映と
 * 輸送開始を行うが、どちらも相手が何をするかを知らずに自分の仕事をする。
 *
 * <p><strong>運ぶのは起きた事実だけである。</strong> 「輸送状態を LOADED にせよ」ではなく
 * 「JPOSA で V001 に積み込んだ」を伝える。どう解釈するかは購読側が決める。
 * 命令を運ぶと、発行側が購読側の都合を知ることになる。
 *
 * @param bookingId        予約 ID
 * @param trackingNumber   追跡番号
 * @param handlingType     荷役種別の名前（{@code RECEIVE} / {@code LOAD} など）
 * @param completionTime   作業日時
 * @param locationUnlocode 作業場所（UN/LOCODE）
 * @param voyageNumber     航海番号。無い場合は {@code null}
 * @param misrouted        予定ルートから外れた作業か（誤配として確定したか）
 */
public record HandlingActivityRegisteredEvent(
        UUID bookingId,
        String trackingNumber,
        String handlingType,
        Instant completionTime,
        String locationUnlocode,
        String voyageNumber,
        boolean misrouted) {
}
