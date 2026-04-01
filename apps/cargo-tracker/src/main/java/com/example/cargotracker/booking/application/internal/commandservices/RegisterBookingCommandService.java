package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RegisterBookingCommandService {

    private final BookingRepository bookingRepository;
    private final ShipperExistencePort shipperExistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterBookingCommandService(BookingRepository bookingRepository,
                                         ShipperExistencePort shipperExistencePort,
                                         ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.shipperExistencePort = shipperExistencePort;
        this.eventPublisher = eventPublisher;
    }

    public BookingId execute(RegisterBookingCommand command) {
        UUID rawShipperId = command.shipperId();

        // 荷主の存在確認（ACL ポート経由）
        shipperExistencePort.verifyExists(rawShipperId);

        // 貨物仕様・輸送条件の生成
        CargoSpecification cargoSpecification = new CargoSpecification(
                command.cargoType(),
                command.weightKg(),
                command.lengthCm(),
                command.widthCm(),
                command.heightCm(),
                command.quantity(),
                command.description()
        );
        TransportCondition transportCondition = new TransportCondition(
                command.originLocation(),
                command.destinationLocation(),
                command.requestedPickupDate(),
                command.requestedDeliveryDate()
        );

        // 予約集約の生成
        BookingId bookingId = BookingId.generate();
        ShipperId shipperId = new ShipperId(rawShipperId);
        Booking booking = Booking.register(bookingId, shipperId, cargoSpecification, transportCondition);

        // 永続化
        bookingRepository.save(booking);

        // ドメインイベント発行
        booking.getDomainEvents().forEach(eventPublisher::publishEvent);

        return bookingId;
    }
}
