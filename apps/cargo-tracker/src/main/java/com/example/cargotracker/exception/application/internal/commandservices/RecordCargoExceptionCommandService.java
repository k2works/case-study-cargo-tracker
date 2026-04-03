package com.example.cargotracker.exception.application.internal.commandservices;

import com.example.cargotracker.exception.application.internal.outboundservices.TrackingExistencePort;
import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.commands.RecordCargoExceptionCommand;
import com.example.cargotracker.exception.domain.model.events.CargoExceptionRecordedEvent;
import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物例外記録コマンドサービス。
 */
@Service
@Transactional
public class RecordCargoExceptionCommandService {

    private final CargoExceptionRepository cargoExceptionRepository;
    private final TrackingExistencePort trackingExistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public RecordCargoExceptionCommandService(CargoExceptionRepository cargoExceptionRepository,
                                              TrackingExistencePort trackingExistencePort,
                                              ApplicationEventPublisher eventPublisher) {
        this.cargoExceptionRepository = cargoExceptionRepository;
        this.trackingExistencePort = trackingExistencePort;
        this.eventPublisher = eventPublisher;
    }

    public ExceptionId execute(RecordCargoExceptionCommand command) {
        // 追跡番号存在確認（ACL ポート経由）
        trackingExistencePort.verifyExists(command.trackingNumber());

        // 貨物例外集約の生成
        ExceptionId id = ExceptionId.generate();
        CargoIncident incident = CargoIncident.create(
                id,
                command.trackingNumber(),
                command.exceptionType(),
                command.locationCode(),
                command.occurredAt(),
                command.reason()
        );
        incident.resolve(command.resolution());
        incident.setEstimatedArrivalDate(command.estimatedArrivalDate());

        // 永続化
        cargoExceptionRepository.save(incident);

        // ドメインイベント発行
        eventPublisher.publishEvent(new CargoExceptionRecordedEvent(
                id,
                command.trackingNumber(),
                command.exceptionType(),
                incident.isUrgent()
        ));

        return id;
    }
}
