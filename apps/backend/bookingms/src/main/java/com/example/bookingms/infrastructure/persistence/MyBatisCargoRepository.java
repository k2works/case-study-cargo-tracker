package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.HazardousDeclaration;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TemperatureRequirement;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCargoRepository implements CargoRepository {

    /** 摂氏以外の単位は扱わない。列は将来のために持つが、書き込むのはこの値だけ。 */
    private static final String CELSIUS = "CELSIUS";

    private final CargoMapper mapper;

    public MyBatisCargoRepository(CargoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Cargo save(Cargo cargo) {
        CargoRecord row = toRecord(cargo);
        mapper.insert(row);
        // 予約番号は DB の DEFAULT が組み立てる。組み立てた結果を読み戻す（ADR-011）
        return findById(row.getId()).orElseThrow(
                () -> new IllegalStateException("保存した予約を読み戻せません: id=" + row.getId()));
    }

    @Override
    public Optional<Cargo> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id)).map(MyBatisCargoRepository::toDomain);
    }

    @Override
    public List<Cargo> search(CargoType type, String keyword, int limit) {
        return mapper.search(nameOf(type), normalize(keyword), limit).stream()
                .map(MyBatisCargoRepository::toDomain)
                .toList();
    }

    @Override
    public long count(CargoType type, String keyword) {
        return mapper.count(nameOf(type), normalize(keyword));
    }

    private static String nameOf(CargoType type) {
        return type == null ? null : type.name();
    }

    private static String normalize(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private static CargoRecord toRecord(Cargo cargo) {
        CargoSpecification specification = cargo.specification();
        RouteSpecification route = cargo.routeSpecification();

        CargoRecord row = new CargoRecord();
        row.setShipperId(cargo.shipperId());
        row.setBookingStatus(cargo.bookingStatus().name());
        row.setTransportStatus(cargo.transportStatus().name());
        row.setRoutingStatus(cargo.routingStatus().name());
        row.setCargoType(specification.type().name());
        row.setWeightKg(specification.weightKg());
        row.setQuantity(specification.quantity());
        row.setDescription(specification.description());
        if (specification.dimensions() != null) {
            row.setLengthCm(specification.dimensions().lengthCm());
            row.setWidthCm(specification.dimensions().widthCm());
            row.setHeightCm(specification.dimensions().heightCm());
        }
        row.setSpecOriginUnlocode(route.origin().unLocode());
        row.setSpecDestinationUnlocode(route.destination().unLocode());
        row.setSpecArrivalDeadline(route.arrivalDeadline());
        row.setSpecDepartureDate(route.departureDate().orElse(null));
        cargo.hazardousDeclaration().ifPresent(declaration -> {
            row.setHazardousClass(declaration.hazardousClass());
            row.setUnNumber(declaration.unNumber());
            row.setProperShippingName(declaration.properShippingName());
        });
        cargo.temperatureRequirement().ifPresent(requirement -> {
            row.setTempMin(requirement.minCelsius());
            row.setTempMax(requirement.maxCelsius());
            row.setTempUnit(CELSIUS);
        });
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private static Cargo toDomain(CargoRecord row) {
        Dimensions dimensions = row.getLengthCm() == null || row.getWidthCm() == null
                || row.getHeightCm() == null
                ? null
                : Dimensions.of(row.getLengthCm(), row.getWidthCm(), row.getHeightCm());

        HazardousDeclaration declaration = row.getUnNumber() == null
                ? null
                : HazardousDeclaration.restore(
                        row.getHazardousClass(), row.getUnNumber(), row.getProperShippingName());

        TemperatureRequirement temperature = row.getTempMin() == null || row.getTempMax() == null
                ? null
                : TemperatureRequirement.restore(row.getTempMin(), row.getTempMax());

        CargoSpecification specification = new CargoSpecification(
                CargoType.valueOf(row.getCargoType()), row.getWeightKg(), row.getQuantity(),
                row.getDescription(), dimensions, declaration, temperature);

        RouteSpecification route = RouteSpecification.restore(
                Location.of(row.getSpecOriginUnlocode(), row.getSpecOriginName()),
                Location.of(row.getSpecDestinationUnlocode(), row.getSpecDestinationName()),
                row.getSpecDepartureDate(),
                row.getSpecArrivalDeadline());

        return Cargo.restore(row.getId(), BookingId.of(row.getBookingId()), row.getShipperId(),
                BookingStatus.valueOf(row.getBookingStatus()),
                TransportStatus.valueOf(row.getTransportStatus()),
                RoutingStatus.valueOf(row.getRoutingStatus()),
                specification, route);
    }
}
