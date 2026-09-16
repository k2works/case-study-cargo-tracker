package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 荷主への通知履歴（US12 §受入基準 4）。
 *
 * <p><b>採番しない。</b> 識別子を内容（予約 ID と通知日時）から導くので、同じ
 * イベントを 2 度読んでも行が増えない（ADR-0008 と同じ形）。</p>
 */
@Mapper
public interface CargoNotificationMapper {

    /** 同じ通知を 2 度読んでも増やさない。リプレイで履歴が水増しされる。 */
    int insert(CargoNotificationRow row);

    /** 新しい通知が先。予約詳細（S22）はこの順で読む。 */
    List<CargoNotificationRow> findByBooking(@Param("bookingId") String bookingId);

    record CargoNotificationRow(
            String bookingId,
            Instant notifiedAt,
            String recipientEmail,
            String summary,
            String notifiedBy) {
    }
}
