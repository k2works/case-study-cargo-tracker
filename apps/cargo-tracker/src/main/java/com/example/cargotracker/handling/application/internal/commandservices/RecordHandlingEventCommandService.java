package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.handling.application.internal.outboundservices.BookingExistencePort;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        // RECEIVE 重複防止チェック
        if (command.eventType() == HandlingEventType.RECEIVE) {
            List<HandlingEvent> existing = handlingEventRepository.findByBookingId(command.bookingId());
            if (!HandlingEvent.canReceive(existing)) {
                throw new DuplicateReceiveException(command.bookingId());
            }
        }

        // 荷役イベント集約の生成
        HandlingEventId id = HandlingEventId.generate();
        HandlingEvent event = HandlingEvent.recordEvent(
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
