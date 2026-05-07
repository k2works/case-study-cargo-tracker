package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 貨物コマンドサービス（予約登録）
 */
@Service
public class CargoCommandService {

    private final CargoRepository cargoRepository;

    public CargoCommandService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    /**
     * 貨物予約を登録する
     *
     * @param shipperId                荷主 ID
     * @param cargoType                貨物種別
     * @param weightKg                 重量（kg）
     * @param originUnlocode           出発地 UNLOCODE
     * @param destinationUnlocode      到着地 UNLOCODE
     * @param arrivalDeadline          到着期限
     * @return 登録された貨物
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

    private String generateBookingId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
