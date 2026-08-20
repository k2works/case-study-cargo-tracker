package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.HazardousDeclaration;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.TemperatureRequirement;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.ZoneId;

/**
 * 貨物予約を登録する。
 *
 * <p>荷主と地点が実在することはここで確かめる。集約は「実在するもの同士の組み合わせ」の
 * 妥当性だけを見る。存在しない荷主 ID を通すと、誰の貨物か分からない予約が保存される。
 */
public class BookCargoUseCase {

    private final CargoRepository cargoes;
    private final ShipperRepository shippers;
    private final LocationRepository locations;
    private final Clock clock;

    public BookCargoUseCase(CargoRepository cargoes, ShipperRepository shippers,
            LocationRepository locations, Clock clock) {
        this.cargoes = cargoes;
        this.shippers = shippers;
        this.locations = locations;
        this.clock = clock;
    }

    public Cargo book(BookCargoCommand command) {
        if (command.shipperId() == null || shippers.findById(command.shipperId()).isEmpty()) {
            throw new IllegalArgumentException("指定された荷主が見つかりません: " + command.shipperId());
        }

        Location origin = locationOf(command.originUnLocode(), "出発地");
        Location destination = locationOf(command.destinationUnLocode(), "目的地");

        // 到着期限は目的地の暦で判断する。UTC で判断すると、時差の分だけ
        // 受付が拒否される時間帯ができる（ADR-010）
        ZoneId destinationZone = locations.timeZoneOf(command.destinationUnLocode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "目的地の業務タイムゾーンが登録されていません: " + command.destinationUnLocode()));

        RouteSpecification route = RouteSpecification.of(origin, destination,
                command.departureDate(), command.arrivalDeadline(), destinationZone, clock);

        return cargoes.save(Cargo.book(command.shipperId(), specificationOf(command), route));
    }

    private Location locationOf(String unLocode, String label) {
        return locations.findByUnLocode(unLocode).orElseThrow(
                () -> new IllegalArgumentException("%sが見つかりません: %s".formatted(label, unLocode)));
    }

    private static CargoSpecification specificationOf(BookCargoCommand command) {
        return new CargoSpecification(
                command.type(),
                command.weightKg(),
                command.quantity(),
                command.description(),
                dimensionsOf(command),
                declarationOf(command),
                temperatureOf(command));
    }

    private static Dimensions dimensionsOf(BookCargoCommand command) {
        // 3 辺そろって初めて意味を持つ。1 辺だけ入力された寸法は捨てず、集約に届く前に
        // ここで拒む（黙って捨てると「入れたのに保存されない」が起きる）
        boolean none = command.lengthCm() == null && command.widthCm() == null
                && command.heightCm() == null;
        if (none) {
            return null;
        }
        return Dimensions.of(command.lengthCm(), command.widthCm(), command.heightCm());
    }

    private static HazardousDeclaration declarationOf(BookCargoCommand command) {
        boolean none = isBlank(command.hazardousClass()) && isBlank(command.unNumber())
                && isBlank(command.properShippingName());
        if (none) {
            return null;
        }
        return HazardousDeclaration.of(
                command.hazardousClass(), command.unNumber(), command.properShippingName());
    }

    private static TemperatureRequirement temperatureOf(BookCargoCommand command) {
        if (command.minCelsius() == null && command.maxCelsius() == null) {
            return null;
        }
        return TemperatureRequirement.of(command.minCelsius(), command.maxCelsius());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
