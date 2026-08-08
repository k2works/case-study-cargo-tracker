package com.example.cargotracker.booking.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通知記録の MyBatis マッパー（US12）。
 *
 * <p>UUID のパラメータには TypeHandler を明示する（{@code ShipperMapper} と同じ理由）。
 */
@Mapper
public interface BookingNotificationMapper {

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO booking_notification (
                booking_id, notification_type, recipient_email, content,
                sent_at, sent_by, result, failure_reason)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{notificationType}, #{recipientEmail}, #{content},
                #{sentAt}, #{sentBy}, #{result}, #{failureReason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookingNotificationRecord row);

    /** 新しい順に返す。**最後に何を送ったかが最初に見たい情報である。** */
    @Select("""
            SELECT id, booking_id AS bookingId, notification_type AS notificationType,
                   recipient_email AS recipientEmail, content, sent_at AS sentAt,
                   sent_by AS sentBy, result, failure_reason AS failureReason
              FROM booking_notification
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
             ORDER BY sent_at DESC, id DESC
            """)
    List<BookingNotificationRecord> findByBookingId(@Param("bookingId") UUID bookingId);
}
