package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisCargoRepository implements CargoRepository {

    private final CargoMapper cargoMapper;

    public MyBatisCargoRepository(CargoMapper cargoMapper) {
        this.cargoMapper = cargoMapper;
    }

    @Override
    public void save(Cargo cargo) {
        cargoMapper.insert(toRecord(cargo));
    }

    @Override
    public Optional<Cargo> findByBookingId(BookingId bookingId) {
        return Optional.ofNullable(cargoMapper.findByBookingId(bookingId.toString())).map(this::toDomain);
    }

    @Override
    public List<Cargo> findAll() {
        return cargoMapper.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Cargo> findByShipperId(ShipperId shipperId) {
        return cargoMapper.findByShipperId(shipperId.toString()).stream().map(this::toDomain).toList();
    }

    private CargoRecord toRecord(Cargo cargo) {
        CargoRecord cargoRecord = new CargoRecord();
        cargoRecord.setBookingId(cargo.getBookingId().toString());
        cargoRecord.setShipperId(cargo.getShipperId().toString());
        cargoRecord.setCargoType(cargo.getCargoType().name());
        cargoRecord.setWeight(cargo.getWeight());
        cargoRecord.setOriginUnlocode(cargo.getRouteSpecification().origin().unlocode());
        cargoRecord.setDestinationUnlocode(cargo.getRouteSpecification().destination().unlocode());
        cargoRecord.setArrivalDeadline(cargo.getRouteSpecification().arrivalDeadline());
        cargoRecord.setBookingStatus(cargo.getStatus().name());
        return cargoRecord;
    }

    private Cargo toDomain(CargoRecord cargoRecord) {
        return new Cargo(
                new BookingId(UUID.fromString(cargoRecord.getBookingId())),
                new ShipperId(UUID.fromString(cargoRecord.getShipperId())),
                CargoType.valueOf(cargoRecord.getCargoType()),
                cargoRecord.getWeight(),
                new RouteSpecification(
                        new Location(cargoRecord.getOriginUnlocode()),
                        new Location(cargoRecord.getDestinationUnlocode()),
                        cargoRecord.getArrivalDeadline()
                ),
                BookingStatus.valueOf(cargoRecord.getBookingStatus())
        );
    }
}
