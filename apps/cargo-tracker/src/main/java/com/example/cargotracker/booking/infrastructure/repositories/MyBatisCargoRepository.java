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
        CargoRecord record = new CargoRecord();
        record.setBookingId(cargo.getBookingId().toString());
        record.setShipperId(cargo.getShipperId().toString());
        record.setCargoType(cargo.getCargoType().name());
        record.setWeight(cargo.getWeight());
        record.setOriginUnlocode(cargo.getRouteSpecification().getOrigin().getUnlocode());
        record.setDestinationUnlocode(cargo.getRouteSpecification().getDestination().getUnlocode());
        record.setArrivalDeadline(cargo.getRouteSpecification().getArrivalDeadline());
        record.setBookingStatus(cargo.getStatus().name());
        return record;
    }

    private Cargo toDomain(CargoRecord record) {
        return new Cargo(
                new BookingId(UUID.fromString(record.getBookingId())),
                new ShipperId(UUID.fromString(record.getShipperId())),
                CargoType.valueOf(record.getCargoType()),
                record.getWeight(),
                new RouteSpecification(
                        new Location(record.getOriginUnlocode()),
                        new Location(record.getDestinationUnlocode()),
                        record.getArrivalDeadline()
                ),
                BookingStatus.valueOf(record.getBookingStatus())
        );
    }
}
