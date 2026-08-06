package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link CargoRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisCargoRepository implements CargoRepository {

    private final CargoMapper mapper;

    public MyBatisCargoRepository(CargoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Cargo cargo) {
        mapper.insert(toRecord(cargo));
    }

    @Override
    public boolean update(Cargo cargo) {
        return mapper.updateStatus(toRecord(cargo)) == 1;
    }

    @Override
    public Optional<Cargo> findById(BookingId bookingId) {
        return Optional.ofNullable(mapper.findByBookingId(bookingId.value()))
                .map(MyBatisCargoRepository::toDomain);
    }

    private static CargoRecord toRecord(Cargo cargo) {
        CargoSpecification spec = cargo.cargoSpecification();
        RouteSpecification route = cargo.routeSpecification();
        CargoRecord record = new CargoRecord();
        record.setBookingId(cargo.bookingId().value());
        record.setShipperId(cargo.shipperId().value());
        record.setCargoType(spec.cargoType().name());
        record.setWeight(spec.weight().kilograms());
        record.setOriginUnlocode(route.origin().unlocode());
        record.setDestinationUnlocode(route.destination().unlocode());
        record.setArrivalDeadline(route.arrivalDeadline());
        record.setBookingStatus(cargo.bookingStatus().name());
        if (spec.dimensions() != null) {
            record.setDimensionLength(spec.dimensions().length());
            record.setDimensionWidth(spec.dimensions().width());
            record.setDimensionHeight(spec.dimensions().height());
        }
        record.setQuantity(spec.quantity() == null ? null : spec.quantity().value());
        record.setDescription(spec.description() == null ? null : spec.description().value());
        record.setVersion(cargo.version());
        return record;
    }

    private static Cargo toDomain(CargoRecord record) {
        CargoSpecification spec = new CargoSpecification(
                CargoType.valueOf(record.getCargoType()),
                Weight.ofKilograms(record.getWeight()),
                Dimensions.ofNullableCentimeters(
                        record.getDimensionLength(),
                        record.getDimensionWidth(),
                        record.getDimensionHeight()),
                Quantity.ofNullable(record.getQuantity()),
                Description.ofNullable(record.getDescription()));

        // 復元時は到着期限の未来日チェックを行わない。**過去になった予約を
        // 読み出せなくなると、期限を過ぎた貨物の追跡もキャンセルもできなくなる。**
        RouteSpecification route = new RouteSpecification(
                Location.of(record.getOriginUnlocode()),
                Location.of(record.getDestinationUnlocode()),
                record.getArrivalDeadline());

        return Cargo.reconstruct(
                new BookingId(record.getBookingId()),
                new ShipperId(record.getShipperId()),
                spec,
                route,
                BookingStatus.valueOf(record.getBookingStatus()),
                record.getVersion());
    }
}
