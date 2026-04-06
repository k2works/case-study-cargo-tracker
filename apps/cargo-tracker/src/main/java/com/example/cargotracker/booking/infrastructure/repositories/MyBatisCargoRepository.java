package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
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
        if (cargo.getDimensions() != null) {
            cargoRecord.setDimensionLength(cargo.getDimensions().length());
            cargoRecord.setDimensionWidth(cargo.getDimensions().width());
            cargoRecord.setDimensionHeight(cargo.getDimensions().height());
        }
        cargoRecord.setQuantity(cargo.getQuantity() != null ? cargo.getQuantity().value() : null);
        cargoRecord.setDescription(cargo.getDescription() != null ? cargo.getDescription().value() : null);
        cargoRecord.setOriginUnlocode(cargo.getRouteSpecification().origin().unlocode());
        cargoRecord.setDestinationUnlocode(cargo.getRouteSpecification().destination().unlocode());
        cargoRecord.setArrivalDeadline(cargo.getRouteSpecification().arrivalDeadline());
        cargoRecord.setBookingStatus(cargo.getStatus().name());
        if (cargo.getHazardousDeclaration() != null) {
            cargoRecord.setHazardousClass(cargo.getHazardousDeclaration().hazardousClass());
            cargoRecord.setUnNumber(cargo.getHazardousDeclaration().unNumber());
            cargoRecord.setProperShippingName(cargo.getHazardousDeclaration().properShippingName());
        }
        if (cargo.getTemperatureRequirement() != null) {
            cargoRecord.setMinTemperature(cargo.getTemperatureRequirement().minTemperature());
            cargoRecord.setMaxTemperature(cargo.getTemperatureRequirement().maxTemperature());
            cargoRecord.setTemperatureUnit(cargo.getTemperatureRequirement().unit().name());
        }
        return cargoRecord;
    }

    private Cargo toDomain(CargoRecord cargoRecord) {
        Dimensions dimensions = toDimensions(cargoRecord);
        Quantity quantity = cargoRecord.getQuantity() != null ? new Quantity(cargoRecord.getQuantity()) : null;
        Description description = cargoRecord.getDescription() != null ? new Description(cargoRecord.getDescription()) : null;

        return new Cargo(
                new BookingId(UUID.fromString(cargoRecord.getBookingId())),
                new ShipperId(UUID.fromString(cargoRecord.getShipperId())),
                CargoType.valueOf(cargoRecord.getCargoType()),
                cargoRecord.getWeight(),
                dimensions,
                quantity,
                description,
                new RouteSpecification(
                        new Location(cargoRecord.getOriginUnlocode()),
                        new Location(cargoRecord.getDestinationUnlocode()),
                        cargoRecord.getArrivalDeadline()
                ),
                BookingStatus.valueOf(cargoRecord.getBookingStatus()),
                toHazardousDeclaration(cargoRecord),
                toTemperatureRequirement(cargoRecord)
        );
    }

    private com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration toHazardousDeclaration(CargoRecord record) {
        if (record.getHazardousClass() != null && record.getUnNumber() != null && record.getProperShippingName() != null) {
            return new com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration(
                    record.getHazardousClass(), record.getUnNumber(), record.getProperShippingName());
        }
        return null;
    }

    private com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement toTemperatureRequirement(CargoRecord record) {
        if (record.getMinTemperature() != null && record.getMaxTemperature() != null && record.getTemperatureUnit() != null) {
            return new com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement(
                    record.getMinTemperature(), record.getMaxTemperature(),
                    com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit.valueOf(record.getTemperatureUnit()));
        }
        return null;
    }

    private Dimensions toDimensions(CargoRecord record) {
        if (record.getDimensionLength() != null && record.getDimensionWidth() != null && record.getDimensionHeight() != null) {
            return new Dimensions(record.getDimensionLength(), record.getDimensionWidth(), record.getDimensionHeight());
        }
        return null;
    }
}
