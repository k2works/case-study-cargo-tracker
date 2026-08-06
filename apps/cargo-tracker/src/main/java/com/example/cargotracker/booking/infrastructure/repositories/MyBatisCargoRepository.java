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
        CargoRecord row = new CargoRecord();
        row.setBookingId(cargo.bookingId().value());
        row.setShipperId(cargo.shipperId().value());
        row.setCargoType(spec.cargoType().name());
        row.setWeight(spec.weight().kilograms());
        row.setOriginUnlocode(route.origin().unlocode());
        row.setDestinationUnlocode(route.destination().unlocode());
        row.setArrivalDeadline(route.arrivalDeadline());
        row.setBookingStatus(cargo.bookingStatus().name());
        if (spec.dimensions() != null) {
            row.setDimensionLength(spec.dimensions().length());
            row.setDimensionWidth(spec.dimensions().width());
            row.setDimensionHeight(spec.dimensions().height());
        }
        row.setQuantity(spec.quantity() == null ? null : spec.quantity().value());
        row.setDescription(spec.description() == null ? null : spec.description().value());
        row.setVersion(cargo.version());
        return row;
    }

    private static Cargo toDomain(CargoRecord row) {
        CargoSpecification spec = new CargoSpecification(
                CargoType.valueOf(row.getCargoType()),
                Weight.ofKilograms(row.getWeight()),
                Dimensions.ofNullableCentimeters(
                        row.getDimensionLength(),
                        row.getDimensionWidth(),
                        row.getDimensionHeight()),
                Quantity.ofNullable(row.getQuantity()),
                Description.ofNullable(row.getDescription()));

        // 復元時は到着期限の未来日チェックを行わない。**過去になった予約を
        // 読み出せなくなると、期限を過ぎた貨物の追跡もキャンセルもできなくなる。**
        RouteSpecification route = new RouteSpecification(
                Location.of(row.getOriginUnlocode()),
                Location.of(row.getDestinationUnlocode()),
                row.getArrivalDeadline());

        return Cargo.reconstruct(
                new BookingId(row.getBookingId()),
                new ShipperId(row.getShipperId()),
                spec,
                route,
                BookingStatus.valueOf(row.getBookingStatus()),
                row.getVersion());
    }
}
