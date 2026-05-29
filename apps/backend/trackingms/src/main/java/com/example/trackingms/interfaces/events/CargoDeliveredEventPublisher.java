package com.example.trackingms.interfaces.events;

import com.example.shared.events.CargoDeliveredEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 配送完了 cross-service イベント発行（US16 / IT5 4.2 / レビュー H3 冪等化対応）。
 *
 * <p>{@link TransportStatusUpdatedEvent} が DELIVERED 遷移を表すときに、shared モジュールの
 * {@link CargoDeliveredEvent} を Kafka 経由で発行する。本イベントは IT7 Billing で
 * {@code CalculateInvoiceCommand} の発行トリガーとして購読される。</p>
 *
 * <h2>冪等性（IT5 レビュー H3 対応）</h2>
 *
 * <p>event store リプレイで DELIVERED 遷移が再生されると二重発行されるリスクがあったため、
 * {@code tracking_summary.delivered_published_at} 列で発行記録を一意管理する。
 * {@code markDeliveredPublished} は {@code delivered_published_at IS NULL} の場合のみ更新する
 * 楽観的ロック相当で、{@code updated > 0} のときだけ {@code eventGateway.publish} を呼ぶ。
 * これにより同一 trackingNumber について Kafka 配信は最大 1 回となる。</p>
 *
 * <p>tracking_summary が未到着（極稀）なら WARN ログを出して発行スキップする
 * （再処理時にリプレイ可能）。</p>
 */
@Component
@ProcessingGroup("cargo-delivered-publisher")
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

        // 冪等化（H3）：UPDATE ... WHERE delivered_published_at IS NULL で「未発行のみ」発行する。
        // event store リプレイで二度購読されても、2 回目以降は updated = 0 で publish しない。
        LocalDateTime publishedAt = LocalDateTime.now();
        int updated = summaryMapper.markDeliveredPublished(event.trackingNumber(), publishedAt);
        if (updated == 0) {
            log.info("[cargo-delivered] trackingNumber={} は既に発行済みのためスキップ（idempotent）",
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
