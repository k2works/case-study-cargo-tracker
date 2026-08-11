package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 通知履歴の読み取り（US12。CQRS のクエリ側）。 */
@Service
public class BookingNotificationQueryService {

    private final BookingNotificationRepository repository;

    public BookingNotificationQueryService(BookingNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 予約の通知履歴を新しい順に返す。
     *
     * <p>予約 ID の形式が不正なら空を返す。<strong>履歴の表示で 500 にしない。</strong>
     */
    public List<BookingNotificationView> findByBookingId(String bookingId) {
        BookingId id;
        try {
            id = BookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return repository.findByBookingId(id).stream()
                .map(n -> new BookingNotificationView(
                        n.delivery().sentAt(),
                        n.delivery().sentBy(),
                        n.recipientEmail(),
                        n.type().displayName(),
                        new BookingNotificationView.Result(
                                n.delivery().result().displayName(),
                                n.delivery().result().badgeClass(),
                                n.delivery().failureReason()),
                        n.content()))
                .toList();
    }
}
