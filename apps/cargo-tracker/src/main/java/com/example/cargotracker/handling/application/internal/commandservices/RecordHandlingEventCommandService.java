package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.handling.application.internal.outboundservices.BookingExistencePort;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役イベント記録コマンドサービス。
 */
@Service
@Transactional
public class RecordHandlingEventCommandService {

    private final HandlingEventRepository handlingEventRepository;
    private final BookingExistencePort bookingExistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public RecordHandlingEventCommandService(HandlingEventRepository handlingEventRepository,
                                              BookingExistencePort bookingExistencePort,
                                              ApplicationEventPublisher eventPublisher) {
        this.handlingEventRepository = handlingEventRepository;
        this.bookingExistencePort = bookingExistencePort;
        this.eventPublisher = eventPublisher;
    }

    public HandlingEventId execute(RecordHandlingEventCommand command) {
        // 予約存在確認（ACL ポート経由）
        bookingExistencePort.verifyExists(command.bookingId());

        // 荷役イベント集約の生成
        HandlingEventId id = HandlingEventId.generate();
        HandlingEvent event = HandlingEvent.record(
                id,
                command.bookingId(),
                command.eventType(),
                command.locationCode(),
                command.completionTime(),
                command.memo()
        );

        // 永続化
        handlingEventRepository.save(event);

        // ドメインイベント発行
        event.getDomainEvents().forEach(eventPublisher::publishEvent);

        return id;
    }
}
