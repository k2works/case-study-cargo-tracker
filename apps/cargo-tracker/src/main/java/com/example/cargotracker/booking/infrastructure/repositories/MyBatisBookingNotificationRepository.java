package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.aggregates.BookingNotification;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationDelivery;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationResult;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationType;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 通知記録の永続化（US12）。 */
@Repository
public class MyBatisBookingNotificationRepository implements BookingNotificationRepository {

    private final BookingNotificationMapper mapper;

    public MyBatisBookingNotificationRepository(BookingNotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public BookingNotification save(BookingNotification notification) {
        BookingNotificationRecord row = new BookingNotificationRecord();
        row.setBookingId(notification.bookingId().value());
        row.setNotificationType(notification.type().name());
        row.setRecipientEmail(notification.recipientEmail());
        row.setContent(notification.content());
        row.setSentAt(notification.delivery().sentAt());
        row.setSentBy(notification.delivery().sentBy());
        row.setResult(notification.delivery().result().name());
        row.setFailureReason(notification.delivery().failureReason());
        mapper.insert(row);
        return BookingNotification.reconstruct(
                row.getId(), notification.bookingId(), notification.type(),
                notification.recipientEmail(), notification.content(), notification.delivery());
    }

    @Override
    public List<BookingNotification> findByBookingId(BookingId bookingId) {
        return mapper.findByBookingId(bookingId.value()).stream()
                .map(MyBatisBookingNotificationRepository::toDomain)
                .toList();
    }

    private static BookingNotification toDomain(BookingNotificationRecord row) {
        return BookingNotification.reconstruct(
                row.getId(),
                new BookingId(row.getBookingId()),
                NotificationType.valueOf(row.getNotificationType()),
                row.getRecipientEmail(),
                row.getContent(),
                new NotificationDelivery(
                        row.getSentAt(), row.getSentBy(),
                        NotificationResult.valueOf(row.getResult()), row.getFailureReason()));
    }
}
