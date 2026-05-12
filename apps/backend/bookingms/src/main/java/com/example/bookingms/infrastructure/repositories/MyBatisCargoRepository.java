package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.HazmatInfo;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.TemperatureInfo;
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
        CargoRecord cargoRecord = toRecord(cargo);
        cargoMapper.insertCargo(cargoRecord);
        return reconstruct(cargoRecord);
    }

    @Override
    public void update(Cargo cargo) {
        CargoRecord cargoRecord = new CargoRecord();
        cargoRecord.setId(cargo.getId());
        cargoRecord.setBookingStatus(cargo.getBookingStatus().name());
        cargoRecord.setRoutingStatus(cargo.getCargoItinerary() != null ? "ROUTED" : "NOT_ROUTED");
        cargoMapper.updateCargo(cargoRecord);

        if (cargo.getCargoItinerary() != null) {
            legMapper.deleteByCargoId(cargo.getId());
            List<Leg> legs = cargo.getCargoItinerary().getLegs();
            for (int i = 0; i < legs.size(); i++) {
                Leg leg = legs.get(i);
                LegRecord legRecord = new LegRecord();
                legRecord.setCargoId(cargo.getId());
                legRecord.setVoyageNumber(leg.getVoyageNumber());
                legRecord.setLoadLocationUnlocode(leg.getLoadLocationUnlocode());
                legRecord.setUnloadLocationUnlocode(leg.getUnloadLocationUnlocode());
                legRecord.setLoadTime(leg.getLoadTime());
                legRecord.setUnloadTime(leg.getUnloadTime());
                legRecord.setSeqNumber(i + 1);
                legMapper.insertLeg(legRecord);
            }
        }
    }

    @Override
    public Optional<Cargo> findByBookingId(BookingId bookingId) {
        return cargoMapper.findByBookingId(bookingId.getId())
                .map(cargoRecord -> {
                    List<LegRecord> legRecords = legMapper.findByCargoId(cargoRecord.getId());
                    return reconstruct(cargoRecord, legRecords);
                });
    }

    @Override
    public List<Cargo> findAll() {
        return cargoMapper.findAll().stream()
                .map(cargoRecord -> {
                    List<LegRecord> legRecords = legMapper.findByCargoId(cargoRecord.getId());
                    return reconstruct(cargoRecord, legRecords);
                })
                .toList();
    }

    // --- private helpers ---

    private CargoRecord toRecord(Cargo cargo) {
        CargoRecord cargoRecord = new CargoRecord();
        cargoRecord.setBookingId(cargo.getBookingId().getId());
        cargoRecord.setShipperId(cargo.getShipperId());
        cargoRecord.setBookingStatus(cargo.getBookingStatus().name());
        cargoRecord.setTransportStatus("NOT_RECEIVED");
        cargoRecord.setRoutingStatus("NOT_ROUTED");
        cargoRecord.setCargoType(cargo.getCargoType().name());
        cargoRecord.setWeightKg(cargo.getWeight().getKg());
        cargoRecord.setBookingAmountValue(0);
        cargoRecord.setBookingAmountCurrency("JPY");
        if (cargo.getRouteSpecification() != null) {
            cargoRecord.setSpecOriginUnlocode(cargo.getRouteSpecification().getOriginUnlocode());
            cargoRecord.setSpecDestinationUnlocode(cargo.getRouteSpecification().getDestinationUnlocode());
            cargoRecord.setSpecArrivalDeadline(cargo.getRouteSpecification().getArrivalDeadline());
        }
        if (cargo.getHazmatInfo() != null) {
            cargoRecord.setHazmatUnCode(cargo.getHazmatInfo().unCode());
            cargoRecord.setHazmatHazardClass(cargo.getHazmatInfo().hazardClass());
            cargoRecord.setHazmatPackingGroup(cargo.getHazmatInfo().packingGroup());
        }
        if (cargo.getTemperatureInfo() != null) {
            cargoRecord.setTempMinCelsius(cargo.getTemperatureInfo().minTemperature());
            cargoRecord.setTempMaxCelsius(cargo.getTemperatureInfo().maxTemperature());
            cargoRecord.setTempUnit(cargo.getTemperatureInfo().unit());
        }
        return cargoRecord;
    }

    private Cargo reconstruct(CargoRecord cargoRecord) {
        return reconstruct(cargoRecord, List.of());
    }

    private Cargo reconstruct(CargoRecord cargoRecord, List<LegRecord> legRecords) {
        RouteSpecification spec = null;
        if (cargoRecord.getSpecOriginUnlocode() != null && cargoRecord.getSpecDestinationUnlocode() != null) {
            spec = new RouteSpecification(
                    cargoRecord.getSpecOriginUnlocode(),
                    cargoRecord.getSpecDestinationUnlocode(),
                    cargoRecord.getSpecArrivalDeadline());
        }

        CargoItinerary itinerary = null;
        if (!legRecords.isEmpty()) {
            List<Leg> legs = new ArrayList<>();
            for (LegRecord legRecord : legRecords) {
                legs.add(new Leg(
                        legRecord.getVoyageNumber(),
                        legRecord.getLoadLocationUnlocode(),
                        legRecord.getUnloadLocationUnlocode(),
                        legRecord.getLoadTime(),
                        legRecord.getUnloadTime()));
            }
            itinerary = new CargoItinerary(legs);
        }

        HazmatInfo hazmatInfo = null;
        if (cargoRecord.getHazmatUnCode() != null) {
            hazmatInfo = new HazmatInfo(cargoRecord.getHazmatUnCode(), cargoRecord.getHazmatHazardClass(), cargoRecord.getHazmatPackingGroup());
        }
        TemperatureInfo temperatureInfo = null;
        if (cargoRecord.getTempUnit() != null) {
            temperatureInfo = new TemperatureInfo(cargoRecord.getTempMinCelsius(), cargoRecord.getTempMaxCelsius(), cargoRecord.getTempUnit());
        }

        Cargo.PersistedCargoState state = new Cargo.PersistedCargoState(
                cargoRecord.getId(),
                new BookingId(cargoRecord.getBookingId()),
                cargoRecord.getShipperId(),
                BookingStatus.valueOf(cargoRecord.getBookingStatus()),
                CargoType.valueOf(cargoRecord.getCargoType()),
                new Weight(cargoRecord.getWeightKg()));
        return new Cargo(state,
                new Cargo.RouteDetails(spec, itinerary),
                new Cargo.SpecialCargoInfo(hazmatInfo, temperatureInfo));
    }
}
