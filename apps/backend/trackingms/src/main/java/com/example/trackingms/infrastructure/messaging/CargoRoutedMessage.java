package com.example.trackingms.infrastructure.messaging;

import java.time.Instant;
import java.time.LocalDate;

/**
 * bookingms が流す「経路が決まった」の受け皿（[ADR-024] 決定 4）。
 *
 * <p><strong>相手の型を共有しない。</strong>ここで受けてから Tracking Context の言葉へ
 * 変換する（[ADR-019] の ACL と同じ形）。
 *
 * <p><strong>知らない項目は無視する</strong>（[ADR-022] 決定 3）。
 */
public record CargoRoutedMessage(
        String trackingNumber,
        String bookingId,
        LocalDate estimatedArrival,
        Instant occurredAt) {
}
