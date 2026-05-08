package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis を使った CargoRepository の実装クラス
 */
@Repository
public class MyBatisCargoRepository implements CargoRepository {

    private final CargoMapper cargoMapper;
    private final LegMapper legMapper;

    public MyBatisCargoRepository(CargoMapper cargoMapper, LegMapper legMapper) {
        this.cargoMapper = cargoMapper;
        this.legMapper = legMapper;
    }

    @Override
    public Cargo save(Cargo cargo) {
        CargoRecord record = toRecord(cargo);
        cargoMapper.insertCargo(record);
        return reconstruct(record);
    }

    @Override
    public void update(Cargo cargo) {
        CargoRecord record = new CargoRecord();
        record.setId(cargo.getId());
        record.setBookingStatus(cargo.getBookingStatus().name());
        record.setRoutingStatus(cargo.getCargoItinerary() != null ? "ROUTED" : "NOT_ROUTED");
        cargoMapper.updateCargo(record);

        if (cargo.getCargoItinerary() != null) {
            legMapper.deleteByCargoId(cargo.getId());
            List<Leg> legs = cargo.getCargoItinerary().getLegs();
            for (int i = 0; i < legs.size(); i++) {
                Leg leg = legs.get(i);
                LegRecord lr = new LegRecord();
                lr.setCargoId(cargo.getId());
                lr.setVoyageNumber(leg.getVoyageNumber());
                lr.setLoadLocationUnlocode(leg.getLoadLocationUnlocode());
                lr.setUnloadLocationUnlocode(leg.getUnloadLocationUnlocode());
                lr.setLoadTime(leg.getLoadTime());
                lr.setUnloadTime(leg.getUnloadTime());
                lr.setSeqNumber(i + 1);
                legMapper.insertLeg(lr);
            }
        }
    }

    @Override
    public Optional<Cargo> findByBookingId(BookingId bookingId) {
        return cargoMapper.findByBookingId(bookingId.getId())
                .map(r -> {
                    List<LegRecord> legRecords = legMapper.findByCargoId(r.getId());
                    return reconstruct(r, legRecords);
                });
    }

    @Override
    public List<Cargo> findAll() {
        return cargoMapper.findAll().stream()
                .map(r -> {
                    List<LegRecord> legRecords = legMapper.findByCargoId(r.getId());
                    return reconstruct(r, legRecords);
                })
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
        return reconstruct(r, List.of());
    }

    private Cargo reconstruct(CargoRecord r, List<LegRecord> legRecords) {
        RouteSpecification spec = null;
        if (r.getSpecOriginUnlocode() != null && r.getSpecDestinationUnlocode() != null) {
            spec = new RouteSpecification(
                    r.getSpecOriginUnlocode(),
                    r.getSpecDestinationUnlocode(),
                    r.getSpecArrivalDeadline());
        }

        CargoItinerary itinerary = null;
        if (!legRecords.isEmpty()) {
            List<Leg> legs = new ArrayList<>();
            for (LegRecord lr : legRecords) {
                legs.add(new Leg(
                        lr.getVoyageNumber(),
                        lr.getLoadLocationUnlocode(),
                        lr.getUnloadLocationUnlocode(),
                        lr.getLoadTime(),
                        lr.getUnloadTime()));
            }
            itinerary = new CargoItinerary(legs);
        }

        return new Cargo(
                r.getId(),
                new BookingId(r.getBookingId()),
                r.getShipperId(),
                BookingStatus.valueOf(r.getBookingStatus()),
                CargoType.valueOf(r.getCargoType()),
                new Weight(r.getWeightKg()),
                spec,
                itinerary);
    }
}
