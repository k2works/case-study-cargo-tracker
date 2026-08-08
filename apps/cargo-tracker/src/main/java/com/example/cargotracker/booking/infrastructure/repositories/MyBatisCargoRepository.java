package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoItinerary;
import com.example.cargotracker.booking.domain.model.BookingTrackingNumber;
import com.example.cargotracker.booking.domain.model.CargoProgress;
import com.example.cargotracker.booking.domain.model.CargoRouting;
import com.example.cargotracker.booking.domain.model.Consignee;
import com.example.cargotracker.booking.domain.model.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Leg;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 経路の割り当てを保存する（US09 / US11）。
     *
     * <p><strong>経路状態と旅程を 1 つの操作として書く。</strong> 片方だけが残ると、
     * 「割り当て済なのに区間が無い」「区間はあるが未割り当て」という
     * 業務上あり得ない状態になる。
     *
     * <p>旅程は<strong>丸ごと入れ替える</strong>。前の区間が残ると、
     * どの経路で運ぶのかが読めなくなる。
     */
    @Override
    @Transactional
    public boolean updateRouting(Cargo cargo) {
        if (mapper.updateRouting(toRecord(cargo)) != 1) {
            return false;
        }
        CargoRecord stored = mapper.findByBookingId(cargo.bookingId().value());
        long cargoId = stored.getId();
        mapper.deleteLegs(cargoId);

        if (cargo.cargoItinerary() != null) {
            List<Leg> legs = cargo.cargoItinerary().legs();
            List<LegRecord> rows = new ArrayList<>(legs.size());
            for (int i = 0; i < legs.size(); i++) {
                rows.add(toLegRecord(cargoId, legs.get(i), i + 1));
            }
            mapper.insertLegs(rows);
        }
        return true;
    }

    @Override
    public void updateConsignee(Cargo cargo) {
        mapper.updateConsignee(toRecord(cargo));
    }

    @Override
    public boolean updateTrackingNumber(Cargo cargo) {
        return mapper.updateTrackingNumber(toRecord(cargo)) == 1;
    }

    @Override
    public Optional<Cargo> findByTrackingNumber(String trackingNumber) {
        CargoRecord row = mapper.findByTrackingNumber(trackingNumber);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(row, mapper.findLegs(row.getId())));
    }

    @Override
    public Optional<Cargo> findById(BookingId bookingId) {
        CargoRecord row = mapper.findByBookingId(bookingId.value());
        if (row == null) {
            return Optional.empty();
        }
        // **旅程も一緒に読む。** 読み戻しで落とすと、割り当て済の予約から
        // 区間が消えて「割り当て済なのに経路が分からない」状態になる
        return Optional.of(toDomain(row, mapper.findLegs(row.getId())));
    }

    private static LegRecord toLegRecord(long cargoId, Leg leg, int seq) {
        LegRecord row = new LegRecord();
        row.setCargoId(cargoId);
        row.setVoyageNumber(leg.voyageNumber());
        row.setLoadLocationUnlocode(leg.loadLocation().unlocode());
        row.setUnloadLocationUnlocode(leg.unloadLocation().unlocode());
        row.setLoadTime(leg.loadTime());
        row.setUnloadTime(leg.unloadTime());
        row.setSeqNumber(seq);
        return row;
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
        row.setRoutingStatus(cargo.routingStatus().name());
        row.setTrackingNumber(cargo.trackingNumber() == null
                ? null : cargo.trackingNumber().value());
        if (spec.dimensions() != null) {
            row.setDimensionLength(spec.dimensions().length());
            row.setDimensionWidth(spec.dimensions().width());
            row.setDimensionHeight(spec.dimensions().height());
        }
        row.setQuantity(spec.quantity() == null ? null : spec.quantity().value());
        row.setDescription(spec.description() == null ? null : spec.description().value());
        Consignee consignee = cargo.consignee();
        row.setConsigneeName(consignee == null ? null : consignee.name());
        row.setConsigneeAddress(consignee == null ? null : consignee.address());
        row.setConsigneeEmail(consignee == null ? null : consignee.contactEmail());
        writeSpecialHandling(row, spec);
        row.setVersion(cargo.version());
        return row;
    }

    /**
     * 危険物申告と温度管理条件を行に写す（US05）。
     *
     * <p><strong>種別との整合はここで判断しない。</strong> 危険物でない貨物に
     * 申告が残らないことは {@code CargoSpecification} が保証済みであり、
     * ここでは「あるものを書く」だけにする。
     */
    private static void writeSpecialHandling(CargoRecord row, CargoSpecification spec) {
        if (spec.hasHazardousDeclaration()) {
            row.setHazardousClass(spec.hazardous().hazardClass());
            row.setUnNumber(spec.hazardous().unNumber());
            row.setProperShippingName(spec.hazardous().properShippingName());
        }
        if (spec.hasTemperatureRequirement()) {
            row.setMinTemperature(spec.temperature().minTemperature());
            row.setMaxTemperature(spec.temperature().maxTemperature());
            row.setTemperatureUnit(spec.temperature().unit().name());
        }
    }

    private static Cargo toDomain(CargoRecord row, List<LegRecord> legs) {
        // **復元では種別と申告の整合を求めない。** 列が無かったころの予約が
        // 読めなくなると、その予約の追跡もキャンセルもできなくなる
        CargoSpecification spec = CargoSpecification.reconstruct(
                CargoType.valueOf(row.getCargoType()),
                Weight.ofKilograms(row.getWeight()),
                Dimensions.ofNullableCentimeters(
                        row.getDimensionLength(),
                        row.getDimensionWidth(),
                        row.getDimensionHeight()),
                Quantity.ofNullable(row.getQuantity()),
                Description.ofNullable(row.getDescription()),
                HazardousDeclaration.ofNullable(
                        row.getHazardousClass(), row.getUnNumber(),
                        row.getProperShippingName()).orElse(null),
                TemperatureRequirement.ofNullable(
                        row.getMinTemperature(), row.getMaxTemperature(),
                        row.getTemperatureUnit()).orElse(null));

        // 復元時は到着期限の未来日チェックを行わない。**過去になった予約を
        // 読み出せなくなると、期限を過ぎた貨物の追跡もキャンセルもできなくなる。**
        RouteSpecification route = new RouteSpecification(
                Location.of(row.getOriginUnlocode()),
                Location.of(row.getDestinationUnlocode()),
                row.getArrivalDeadline());

        // **区間が無ければ旅程も無い。** 空の旅程を作ると、連結制約の検証で落ちる
        CargoItinerary itinerary = legs.isEmpty() ? null : CargoItinerary.of(legs.stream()
                .map(leg -> Leg.of(
                        leg.getVoyageNumber(),
                        Location.of(leg.getLoadLocationUnlocode()),
                        Location.of(leg.getUnloadLocationUnlocode()),
                        leg.getLoadTime(),
                        leg.getUnloadTime()))
                .toList());

        return Cargo.reconstruct(
                new BookingId(row.getBookingId()),
                new ShipperId(row.getShipperId()),
                spec,
                route,
                new CargoProgress(
                        BookingStatus.valueOf(row.getBookingStatus()),
                        new CargoRouting(
                                CargoRoutingStatus.valueOf(row.getRoutingStatus()), itinerary),
                        // **読み戻しで落とすと、発行済みの追跡番号が消える**
                        row.getTrackingNumber() == null
                                ? null : new BookingTrackingNumber(row.getTrackingNumber())),
                // 荷受人は予約の時点では未確定でありうる（US16）
                row.getConsigneeName() == null ? null : new Consignee(
                        row.getConsigneeName(),
                        row.getConsigneeAddress(),
                        row.getConsigneeEmail()),
                row.getVersion());
    }
}
