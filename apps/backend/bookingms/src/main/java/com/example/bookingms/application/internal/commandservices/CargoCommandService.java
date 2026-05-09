package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.events.CargoAssignedForRoutingEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.HazmatInfo;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.TemperatureInfo;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoEventPublisher;
import com.example.bookingms.domain.ports.CargoRepository;
import com.example.bookingms.domain.ports.ShipperNotificationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 貨物コマンドサービス（予約登録・経路割当）
 */
@Service
public class CargoCommandService {
    private static final String CARGO_NOT_FOUND_PREFIX = "Cargo not found: ";

    private final CargoRepository cargoRepository;
    private final CargoEventPublisher cargoEventPublisher;
    private final ShipperNotificationPort shipperNotificationPort;

    public CargoCommandService(CargoRepository cargoRepository, CargoEventPublisher cargoEventPublisher,
                               ShipperNotificationPort shipperNotificationPort) {
        this.cargoRepository = cargoRepository;
        this.cargoEventPublisher = cargoEventPublisher;
        this.shipperNotificationPort = shipperNotificationPort;
    }

    /**
     * 貨物予約を登録する
     */
    @Transactional
    public Cargo registerBooking(Long shipperId, String cargoType, BigDecimal weightKg,
                                  String originUnlocode, String destinationUnlocode,
                                  LocalDate arrivalDeadline,
                                  HazmatInfo hazmatInfo, TemperatureInfo temperatureInfo) {
        BookingId bookingId = new BookingId(generateBookingId());
        Weight weight = new Weight(weightKg);
        CargoType type = CargoType.valueOf(cargoType);
        RouteSpecification spec = new RouteSpecification(originUnlocode, destinationUnlocode, arrivalDeadline);

        if (type == CargoType.HAZARDOUS && hazmatInfo == null) {
            throw new IllegalArgumentException("危険物貨物には危険物申告情報が必須です");
        }
        if (type == CargoType.REFRIGERATED && temperatureInfo == null) {
            throw new IllegalArgumentException("冷凍・冷蔵貨物には温度管理条件が必須です");
        }

        Cargo cargo = new Cargo(bookingId, shipperId, type, weight, spec, hazmatInfo, temperatureInfo);
        return cargoRepository.save(cargo);
    }

    /**
     * 経路を割り当てる（RouteCargoCommand）
     * CargoItinerary を Cargo に設定し、予約状態を ROUTE_PROPOSED に遷移させる
     *
     * @param bookingId 予約 ID
     * @param command   経路割当コマンド
     * @return 更新後の貨物
     */
    @Transactional
    public Cargo assignRoute(String bookingId, RouteCargoCommand command) {
        Cargo cargo = findCargoOrThrow(bookingId);

        List<Leg> legs = command.legs().stream()
                .map(l -> new Leg(
                        l.voyageNumber(),
                        l.loadLocationUnlocode(),
                        l.unloadLocationUnlocode(),
                        l.loadTime(),
                        l.unloadTime()))
                .toList();

        CargoItinerary itinerary = new CargoItinerary(legs);
        cargo.assignRoute(itinerary);
        cargoRepository.update(cargo);

        cargoEventPublisher.publishCargoRouted(new CargoRoutedEvent(
                cargo.getBookingId().getId(),
                cargo.getRouteSpecification().getOriginUnlocode(),
                cargo.getRouteSpecification().getDestinationUnlocode()
        ));

        shipperNotificationPort.notifyRouteAssigned(cargo);

        return cargo;
    }

    /**
     * 予約を確定する（UpdateBookingStatusCommand）
     *
     * @param bookingId 予約 ID
     * @return 更新後の貨物
     */
    @Transactional
    public Cargo confirmBooking(String bookingId) {
        Cargo cargo = findCargoOrThrow(bookingId);
        cargo.confirm();
        cargoRepository.update(cargo);

        cargoEventPublisher.publishCargoAssignedForRouting(new CargoAssignedForRoutingEvent(
                cargo.getBookingId().getId(),
                cargo.getRouteSpecification().getOriginUnlocode(),
                cargo.getRouteSpecification().getDestinationUnlocode(),
                cargo.getRouteSpecification().getArrivalDeadline().toString()
        ));

        return cargo;
    }

    /**
     * 予約をキャンセルする
     *
     * @param bookingId 予約 ID
     * @return 更新後の貨物
     */
    @Transactional
    public Cargo cancelBooking(String bookingId) {
        Cargo cargo = findCargoOrThrow(bookingId);
        cargo.cancel();
        cargoRepository.update(cargo);
        return cargo;
    }

    private Cargo findCargoOrThrow(String bookingId) {
        return cargoRepository.findByBookingId(new BookingId(bookingId))
                .orElseThrow(() -> new IllegalArgumentException(CARGO_NOT_FOUND_PREFIX + bookingId));
    }

    private String generateBookingId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
