package com.example.trackingms.interfaces.events;

import com.example.shared.events.CargoDeliveredEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 配送完了 cross-service イベント発行（US16 / IT5 4.2）。
 *
 * <p>{@link TransportStatusUpdatedEvent} が DELIVERED 遷移を表すときに、shared モジュールの
 * {@link CargoDeliveredEvent} を Kafka 経由で発行する。本イベントは IT7 Billing で
 * {@code CalculateInvoiceCommand} の発行トリガーとして購読される。</p>
 *
 * <p>{@code bookingId} は {@code tracking_summary.booking_id} から取得する。
 * 投影が未到着（極稀）なら WARN ログを出して発行スキップする（再処理時にリプレイ可能）。</p>
 */
@Component
public class CargoDeliveredEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CargoDeliveredEventPublisher.class);

    private final EventGateway eventGateway;
    private final TrackingSummaryMapper summaryMapper;

    public CargoDeliveredEventPublisher(EventGateway eventGateway,
                                        TrackingSummaryMapper summaryMapper) {
        this.eventGateway = eventGateway;
        this.summaryMapper = summaryMapper;
    }

    @EventHandler
    public void on(TransportStatusUpdatedEvent event) {
        if (event.toStatus() != TransportStatus.DELIVERED) {
            return;
        }
        TrackingSummary summary = summaryMapper.findByTrackingNumber(event.trackingNumber());
        if (summary == null) {
            log.warn("[cargo-delivered] tracking_summary 未到着のため CargoDeliveredEvent 発行をスキップ "
                            + "(trackingNumber={})",
                    event.trackingNumber());
            return;
        }
        CargoDeliveredEvent delivered = new CargoDeliveredEvent(
                event.trackingNumber(),
                summary.getBookingId(),
                event.occurredAt()
        );
        eventGateway.publish(delivered);
        log.info("[cargo-delivered] published trackingNumber={} bookingId={} deliveredAt={}",
                event.trackingNumber(), summary.getBookingId(), event.occurredAt());
    }
}
