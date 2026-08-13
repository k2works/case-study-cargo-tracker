package com.example.cargotracker.shared.domain.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 貨物に経路が割り当てられた（US11 / ADR-012）。
 *
 * <p>Booking Context が発行し、Tracking Context が購読する。
 *
 * <p><strong>この経路が存在する理由は循環の解消である。</strong> 追跡は目的地と
 * 推定到着日を表示するが、それを Booking へ問い合わせると Tracking → Booking の
 * 参照が生まれ、Booking → Tracking（追跡番号の発行）と合わせて循環する（ADR-012）。
 *
 * <p>値は追跡番号の発行時に一緒に渡す。<strong>経路が後から変わったときに追随する
 * 手段が本イベントである。</strong> 発行時の受け渡しだけを実装すると、
 * 経路を変えても古い到着予定が残り続ける。
 *
 * <p><strong>運ぶのは素の値だけである</strong>（ADR-005）。
 *
 * @param bookingId              予約 ID
 * @param destinationUnlocode    目的地（UN/LOCODE）
 * @param estimatedArrivalDate   推定到着日。旅程が無ければ {@code null}
 */
public record CargoRoutedEvent(
        UUID bookingId,
        String destinationUnlocode,
        LocalDate estimatedArrivalDate) {
}
