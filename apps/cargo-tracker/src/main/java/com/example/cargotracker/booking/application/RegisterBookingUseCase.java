package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.domain.*;
import com.example.cargotracker.shipper.domain.ShipperId;
import com.example.cargotracker.shipper.domain.ShipperRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RegisterBookingUseCase {

    private final BookingRepository bookingRepository;
    private final ShipperRepository shipperRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterBookingUseCase(BookingRepository bookingRepository,
                                  ShipperRepository shipperRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.shipperRepository = shipperRepository;
        this.eventPublisher = eventPublisher;
    }

    public BookingId execute(RegisterBookingCommand command) {
        UUID rawShipperId = command.shipperId();
        ShipperId shipperId = new ShipperId(rawShipperId);

        // 荷主の存在確認
        shipperRepository.findById(shipperId)
                .orElseThrow(() -> new ShipperNotFoundException(rawShipperId.toString()));

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
        Booking booking = Booking.register(bookingId, shipperId, cargoSpecification, transportCondition);

        // 永続化
        bookingRepository.save(booking);

        // ドメインイベント発行
        booking.getDomainEvents().forEach(eventPublisher::publishEvent);

        return bookingId;
    }
}
