package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis を使った CargoRepository の実装クラス
 */
@Repository
public class MyBatisCargoRepository implements CargoRepository {

    private final CargoMapper cargoMapper;

    public MyBatisCargoRepository(CargoMapper cargoMapper) {
        this.cargoMapper = cargoMapper;
    }

    @Override
    public Cargo save(Cargo cargo) {
        CargoRecord record = toRecord(cargo);
        cargoMapper.insertCargo(record);
        return reconstruct(record);
    }

    @Override
    public Optional<Cargo> findByBookingId(BookingId bookingId) {
        return cargoMapper.findByBookingId(bookingId.getId())
                .map(this::reconstruct);
    }

    @Override
    public List<Cargo> findAll() {
        return cargoMapper.findAll().stream()
                .map(this::reconstruct)
                .toList();
    }

    // --- private helpers ---

    private CargoRecord toRecord(Cargo cargo) {
        CargoRecord r = new CargoRecord();
        r.setBookingId(cargo.getBookingId().getId());
        r.setShipperId(cargo.getShipperId());
        r.setBookingStatus(cargo.getBookingStatus().name());
        r.setTransportStatus("NOT_RECEIVED");
        r.setRoutingStatus("NOT_ROUTED");
        r.setCargoType(cargo.getCargoType().name());
        r.setWeightKg(cargo.getWeight().getKg());
        r.setBookingAmountValue(0);
        r.setBookingAmountCurrency("JPY");
        if (cargo.getRouteSpecification() != null) {
            r.setSpecOriginUnlocode(cargo.getRouteSpecification().getOriginUnlocode());
            r.setSpecDestinationUnlocode(cargo.getRouteSpecification().getDestinationUnlocode());
            r.setSpecArrivalDeadline(cargo.getRouteSpecification().getArrivalDeadline());
        }
        return r;
    }

    private Cargo reconstruct(CargoRecord r) {
        RouteSpecification spec = null;
        if (r.getSpecOriginUnlocode() != null && r.getSpecDestinationUnlocode() != null) {
            spec = new RouteSpecification(
                    r.getSpecOriginUnlocode(),
                    r.getSpecDestinationUnlocode(),
                    r.getSpecArrivalDeadline());
        }
        return new Cargo(
                r.getId(),
                new BookingId(r.getBookingId()),
                r.getShipperId(),
                BookingStatus.valueOf(r.getBookingStatus()),
                CargoType.valueOf(r.getCargoType()),
                new Weight(r.getWeightKg()),
                spec);
    }
}
