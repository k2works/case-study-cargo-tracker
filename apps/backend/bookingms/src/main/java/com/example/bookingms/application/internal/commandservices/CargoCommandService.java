package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 貨物コマンドサービス（予約登録・経路割当）
 */
@Service
public class CargoCommandService {

    private final CargoRepository cargoRepository;

    public CargoCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 貨物予約を登録する
     */
    public Cargo registerBooking(Long shipperId, String cargoType, BigDecimal weightKg,
                                  String originUnlocode, String destinationUnlocode,
                                  LocalDate arrivalDeadline) {
        BookingId bookingId = new BookingId(generateBookingId());
        Weight weight = new Weight(weightKg);
        CargoType type = CargoType.valueOf(cargoType);
        RouteSpecification spec = new RouteSpecification(originUnlocode, destinationUnlocode, arrivalDeadline);

        Cargo cargo = new Cargo(bookingId, shipperId, type, weight, spec);
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
    public Cargo assignRoute(String bookingId, RouteCargoCommand command) {
        Cargo cargo = cargoRepository.findByBookingId(new BookingId(bookingId))
                .orElseThrow(() -> new IllegalArgumentException("Cargo not found: " + bookingId));

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
        return cargo;
    }

    private String generateBookingId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
