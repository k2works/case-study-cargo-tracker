package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.aggregates.CargoRestoration;
import com.example.bookingms.domain.model.aggregates.RouteNotification;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.bookingms.domain.model.valueobjects.CargoStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Dimensions;
import com.example.bookingms.domain.model.valueobjects.HazardousDeclaration;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.Misroute;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.bookingms.domain.model.valueobjects.TemperatureRequirement;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;
import com.example.bookingms.domain.model.valueobjects.TransportStatus;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisCargoRepository implements CargoRepository {

    /** 摂氏以外の単位は扱わない。列は将来のために持つが、書き込むのはこの値だけ。 */
    private static final String CELSIUS = "CELSIUS";

    private final CargoMapper mapper;
    private final LegMapper legs;

    /** 業務タイムゾーンの時刻源。追跡番号の日付をここから取る（[ADR-010]）。 */
    private final Clock clock;

    public MyBatisCargoRepository(CargoMapper mapper, LegMapper legs, Clock clock) {
        this.mapper = mapper;
        this.legs = legs;
        this.clock = clock;
    }

    /**
     * 新規なら追加し、既にあるなら書き換える。
     *
     * <p>常に追加すると、経路設計の依頼（US06）のような更新が「新しい予約を作る」動きになる。
     * しかも元の予約は変わらないため、画面には依頼できたように見えて、一覧には依頼済みの
     * 別番号が増える。IT3 の kind 統合環境でこの形で見つかった。
     */
    @Override
    @Transactional
    public Cargo save(Cargo cargo) {
        CargoRecord row = toRecord(cargo);
        if (cargo.id() == null) {
            mapper.insert(row);
        } else {
            row.setId(cargo.id());
            mapper.update(row);
        }
        saveItinerary(row.getId(), cargo.itinerary().orElse(null));
        // 予約番号は DB の DEFAULT が組み立てる。組み立てた結果を読み戻す（ADR-011）
        return findById(row.getId()).orElseThrow(
                () -> new IllegalStateException("保存した予約を読み戻せません: id=" + row.getId()));
    }

    /**
     * 旅程は「消してから入れ直す」（IT3 の航海スケジュールと同じ判断）。
     *
     * <p>差分更新は順序の付け替えが要り、途中で失敗するとつながっていない旅程が残る。
     * 消さずに入れ直すと旅程が二重になり、しかも順序は保たれるため、画面上は
     * 「区間が増えた」ようにしか見えない。
     */
    private void saveItinerary(Long cargoId, CargoItinerary itinerary) {
        legs.deleteByCargoId(cargoId);
        if (itinerary == null) {
            return;
        }
        List<Leg> ordered = itinerary.legs();
        for (int i = 0; i < ordered.size(); i++) {
            legs.insert(toRecord(cargoId, ordered.get(i), i + 1));
        }
    }

    private static LegRecord toRecord(Long cargoId, Leg leg, int seqNumber) {
        LegRecord row = new LegRecord();
        row.setCargoId(cargoId);
        row.setVoyageNumber(leg.voyageNumber().value());
        row.setLoadLocationUnlocode(leg.loadLocation().unLocode());
        row.setUnloadLocationUnlocode(leg.unloadLocation().unLocode());
        row.setLoadTime(leg.loadTime());
        row.setUnloadTime(leg.unloadTime());
        row.setSeqNumber(seqNumber);
        return row;
    }

    /** 復元では検査しない。連結の規則が無かったころの行が読めなくなる。 */
    private CargoItinerary itineraryOf(Long cargoId) {
        List<LegRecord> rows = legs.findByCargoId(cargoId);
        if (rows.isEmpty()) {
            // 空のリストと「旅程が無い」を取り違えると、画面が空の旅程表を出す
            return null;
        }
        return CargoItinerary.restore(rows.stream()
                .map(row -> Leg.restore(
                        VoyageNumber.restore(row.getVoyageNumber()),
                        Location.of(row.getLoadLocationUnlocode(), row.getLoadLocationName()),
                        Location.of(row.getUnloadLocationUnlocode(), row.getUnloadLocationName()),
                        row.getLoadTime(), row.getUnloadTime()))
                .toList());
    }

    @Override
    public Optional<Cargo> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toDomainWithItinerary);
    }

    /**
     * 追跡番号を採番する。
     *
     * <p>組み立て（形式）は DB が行う（[ADR-011]）。<strong>日付だけはこちらが渡す</strong>——
     * {@code CURRENT_DATE} は DB のセッションのタイムゾーンで決まり、業務タイムゾーンの
     * {@link Clock} とずれる。時刻源を 1 つにする。
     */
    @Override
    public String nextTrackingNumber() {
        String businessDate = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        return mapper.nextTrackingNumber(businessDate);
    }

    private Cargo toDomainWithItinerary(CargoRecord row) {
        return withItinerary(toDomain(row), itineraryOf(row.getId()));
    }

    /**
     * 旅程を付けた写しを作る。
     *
     * <p><strong>項目を落とさない。</strong>ここで並べ直すため、集約に項目が増えるたびに
     * 書き足す必要がある。書き忘れると、その項目だけが読み戻しで消える（IT6 で通知の記録と
     * 追跡番号を落とした）。落ちたことは<strong>読み戻しのテストでしか分からない</strong>。
     */
    private static Cargo withItinerary(Cargo cargo, CargoItinerary itinerary) {
        return CargoRestoration.restore(cargo.id(), cargo.bookingId().orElse(null), cargo.shipperId(),
                cargo.status(), cargo.specification(), cargo.routeSpecification(), itinerary,
                cargo.routeNotification().orElse(null), cargo.trackingNumber().orElse(null),
                // **ここで落とすと、読み戻しでだけ消える。**IT9 で実際に落とし、
                // IT10 で誤配の記録をまた落とした（どちらも読み戻しのテストが捕まえた）
                cargo.lastHandlingLocation().orElse(null), cargo.lastHandlingAt().orElse(null),
                cargo.misroute().orElse(null));
    }

    @Override
    public Optional<CargoSummary> findByBookingId(String bookingId) {
        return Optional.ofNullable(mapper.findByBookingId(bookingId))
                .map(row -> new CargoSummary(toDomainWithItinerary(row), row.getShipperName(), row.getShipperCode()));
    }

    @Override
    public Optional<CargoSummary> findByTrackingNumber(String trackingNumber) {
        return Optional.ofNullable(mapper.findByTrackingNumber(trackingNumber))
                .map(row -> new CargoSummary(toDomainWithItinerary(row), row.getShipperName(), row.getShipperCode()));
    }

    @Override
    public List<CargoSummary> findByTrackingNumbers(List<String> trackingNumbers) {
        if (trackingNumbers.isEmpty()) {
            return List.of();
        }
        return mapper.findByTrackingNumbers(trackingNumbers).stream()
                .map(row -> new CargoSummary(toDomain(row), row.getShipperName(),
                        row.getShipperCode()))
                .toList();
    }

    @Override
    public List<CargoSummary> findByShipperId(long shipperId) {
        return mapper.findByShipperId(shipperId).stream()
                .map(row -> new CargoSummary(toDomain(row), row.getShipperName(), row.getShipperCode()))
                .toList();
    }

    @Override
    public List<CargoSummary> search(CargoType type, String keyword,
            Collection<RoutingStatus> routingStatuses, BookingStatus bookingStatus, int limit) {
        return mapper.search(nameOf(type), normalize(keyword), namesOf(routingStatuses),
                        bookingStatus == null ? null : bookingStatus.name(), limit)
                .stream()
                .map(row -> new CargoSummary(toDomain(row), row.getShipperName(), row.getShipperCode()))
                .toList();
    }

    @Override
    public long count(CargoType type, String keyword, Collection<RoutingStatus> routingStatuses,
            BookingStatus bookingStatus) {
        return mapper.count(nameOf(type), normalize(keyword), namesOf(routingStatuses),
                bookingStatus == null ? null : bookingStatus.name());
    }

    private static String nameOf(CargoType type) {
        return type == null ? null : type.name();
    }

    /** 空の絞り込みは「絞らない」。空のリストを SQL に渡すと `IN ()` になり解釈できない。 */
    private static List<String> namesOf(Collection<RoutingStatus> routingStatuses) {
        return routingStatuses == null || routingStatuses.isEmpty()
                ? null
                : routingStatuses.stream().map(RoutingStatus::name).toList();
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
            row.setHazardousClass(declaration.hazardousClass().code());
            row.setUnNumber(declaration.unNumber());
            row.setProperShippingName(declaration.properShippingName());
        });
        cargo.temperatureRequirement().ifPresent(requirement -> {
            row.setTempMin(requirement.minCelsius());
            row.setTempMax(requirement.maxCelsius());
            row.setTempUnit(CELSIUS);
        });
        cargo.routeNotification().ifPresent(notification -> {
            row.setRouteNotifiedAt(notification.notifiedAt());
            row.setRouteNotifiedBy(notification.notifiedBy());
        });
        row.setTrackingNumber(cargo.trackingNumber().map(TrackingNumber::value).orElse(null));
        // 最後の荷役（[ADR-025] 決定 4）。**書かないと、陸揚げ地の候補に現在地が出ない**
        row.setLastHandlingLocationUnlocode(cargo.lastHandlingLocation().orElse(null));
        row.setLastHandlingAt(cargo.lastHandlingAt().orElse(null));
        // **誤配の事実も運ぶ。**書き忘れると、この項目だけが読み戻しで消える
        // ——料金調整の根拠が失われ、請求の段まで気づかれない
        row.setMisroutedAt(cargo.misroute().map(Misroute::at).orElse(null));
        row.setMisroutedLocationUnlocode(
                cargo.misroute().map(Misroute::locationUnLocode).orElse(null));
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private static Cargo toDomain(CargoRecord row) {
        Dimensions dimensions = row.getLengthCm() == null || row.getWidthCm() == null
                || row.getHeightCm() == null
                ? null
                : Dimensions.restore(row.getLengthCm(), row.getWidthCm(), row.getHeightCm());

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

        CargoStatus status = new CargoStatus(
                BookingStatus.valueOf(row.getBookingStatus()),
                TransportStatus.valueOf(row.getTransportStatus()),
                RoutingStatus.valueOf(row.getRoutingStatus()));

        return CargoRestoration.restore(row.getId(), BookingId.restore(row.getBookingId()), row.getShipperId(),
                status, specification, route, null,
                RouteNotification.restore(row.getRouteNotifiedAt(), row.getRouteNotifiedBy()),
                TrackingNumber.restoreNullable(row.getTrackingNumber()),
                row.getLastHandlingLocationUnlocode(), row.getLastHandlingAt(),
                // **復元では検査しない**（[ADR-012]）。2 列は独立した nullable であり、
                // 片方だけ入った行で落とすと予約詳細が 500 になる
                Misroute.restore(row.getMisroutedAt(), row.getMisroutedLocationUnlocode()));
    }
}
