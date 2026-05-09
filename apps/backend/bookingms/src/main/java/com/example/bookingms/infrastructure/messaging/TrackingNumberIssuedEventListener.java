package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.TrackingNumberIssuedEvent;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.ports.CargoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * trackingms からの追跡番号発行イベントを受信するリスナー
 *
 * <p>追跡番号発行イベントを受信し、対応する予約の状態を TRACKING_ISSUED に遷移させる。
 */
@Component
public class TrackingNumberIssuedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TrackingNumberIssuedEventListener.class);

    static final String QUEUE = "booking.tracking.number.issued";

    private final CargoRepository cargoRepository;

    public TrackingNumberIssuedEventListener(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    @RabbitListener(queues = QUEUE)
    @Transactional
    public void handleTrackingNumberIssued(TrackingNumberIssuedEvent event) {
        log.info("TrackingNumberIssuedEvent 受信: bookingId={}, trackingNumber={}",
                event.bookingId(), event.trackingNumber());

        cargoRepository.findByBookingId(new BookingId(event.bookingId()))
                .ifPresentOrElse(
                        cargo -> {
                            if (cargo.getBookingStatus() == BookingStatus.CONFIRMED) {
                                cargo.markTrackingIssued();
                                cargoRepository.update(cargo);
                                log.info("予約 {} の状態を TRACKING_ISSUED に遷移しました", event.bookingId());
                            } else {
                                log.warn("予約 {} は CONFIRMED 状態ではないため状態遷移をスキップしました: {}",
                                        event.bookingId(), cargo.getBookingStatus());
                            }
                        },
                        () -> log.warn("予約 {} が見つかりません", event.bookingId())
                );
    }
}
