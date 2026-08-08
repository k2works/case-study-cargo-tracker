package com.example.cargotracker.handling.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 荷役作業。Handling モジュールの集約ルート（US15 / US16）。
 *
 * <p><strong>予定と違う作業も記録する。</strong> 判定するのは「予定どおりか」で
 * あって「登録してよいか」ではない。拒否すると、実際に起きた作業がどこにも
 * 残らず、追跡は現実と食い違ったまま進む。
 */
public class HandlingActivity {

    private final CargoBookingId cargoBookingId;
    private final HandlingType type;
    private final Instant completionTime;
    private final Location location;
    private final HandlingVoyageNumber voyageNumber;
    private final String operatorName;
    private final long version;

    private HandlingActivity(
            CargoBookingId cargoBookingId,
            HandlingType type,
            Instant completionTime,
            Location location,
            HandlingVoyageNumber voyageNumber,
            String operatorName,
            long version) {
        this.cargoBookingId = cargoBookingId;
        this.type = type;
        this.completionTime = completionTime;
        this.location = location;
        this.voyageNumber = voyageNumber;
        this.operatorName = operatorName;
        this.version = version;
    }

    /**
     * 荷役作業を登録する。
     *
     * <p>積込・荷降しは航海番号が必須である（デシジョンテーブル）。
     * <strong>どの便に対する作業か分からない記録は、誤配の判定にも追跡の表示にも
     * 使えない。</strong>
     */
    public static HandlingActivity register(RegisterHandlingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("荷役登録コマンドは必須です");
        }
        if (command.cargoBookingId() == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        if (command.type() == null) {
            throw new IllegalArgumentException("荷役種別は必須です");
        }
        if (command.completionTime() == null) {
            throw new IllegalArgumentException("作業日時は必須です");
        }
        if (command.location() == null) {
            throw new IllegalArgumentException("作業場所は必須です");
        }
        if (command.type().requiresVoyageNumber() && command.voyageNumber() == null) {
            throw new IllegalArgumentException(
                    "%s には航海番号が必要です".formatted(command.type().displayName()));
        }
        return new HandlingActivity(
                command.cargoBookingId(), command.type(), command.completionTime(),
                command.location(), command.voyageNumber(), command.operatorName(), 0L);
    }

    /** 永続化された状態から復元する。 */
    public static HandlingActivity reconstruct(
            CargoBookingId cargoBookingId,
            HandlingType type,
            Instant completionTime,
            Location location,
            HandlingVoyageNumber voyageNumber,
            String operatorName,
            long version) {
        return new HandlingActivity(cargoBookingId, type, completionTime, location,
                voyageNumber, operatorName, version);
    }

    /**
     * 予約の予定ルートと突き合わせる（{@code domain-model.md} のデシジョンテーブル）。
     *
     * <p>照合先は種別で変わる。
     *
     * <ul>
     *   <li>受領 — 予約の出発地。違えば<strong>警告</strong></li>
     *   <li>積込 — 旅程に「この便でこの港から積む」区間があるか。無ければ<strong>誤配</strong></li>
     *   <li>荷降し — 旅程に「この便でこの港で降ろす」区間があるか。無ければ<strong>誤配</strong></li>
     *   <li>引取 — 予約の目的地。違えば<strong>警告</strong></li>
     *   <li>通関 — 場所を照合しない</li>
     * </ul>
     *
     * <p><strong>経路が割り当てられていない貨物の積込・荷降しは誤配にしない。</strong>
     * 比べる予定そのものが無いためである。予定が無いことを「予定と違う」と呼ぶと、
     * 誤配の件数が意味を失う。
     */
    public HandlingValidation isValidFor(CargoSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("予約の写しは必須です");
        }
        return switch (type) {
            case CUSTOMS -> HandlingValidation.asPlanned();
            case RECEIVE -> matchesEndpoint(snapshot.origin(), "出発地");
            case CLAIM -> matchesEndpoint(snapshot.destination(), "目的地");
            case LOAD, UNLOAD -> matchesItinerary(snapshot);
        };
    }

    private HandlingValidation matchesEndpoint(String expected, String label) {
        if (location.unlocode().equals(expected)) {
            return HandlingValidation.asPlanned();
        }
        return HandlingValidation.warning(
                "予約の%s（%s）と違う場所での%sです: %s"
                        .formatted(label, expected, type.displayName(), location.unlocode()));
    }

    private HandlingValidation matchesItinerary(CargoSnapshot snapshot) {
        if (!snapshot.isRouted()) {
            // 予定が無いので比べようがない。**「予定と違う」とは呼ばない**
            return HandlingValidation.warning(
                    "経路が割り当てられていないため、予定ルートと照合できません");
        }
        boolean onPlan = snapshot.itineraryLegs().stream().anyMatch(leg ->
                leg.voyageNumber().equals(voyageNumber.value())
                        && location.unlocode().equals(
                                type == HandlingType.LOAD
                                        ? leg.loadLocation() : leg.unloadLocation()));
        if (onPlan) {
            return HandlingValidation.asPlanned();
        }
        return HandlingValidation.misrouted(
                "予定ルートに無い%sです: %s 便 / %s"
                        .formatted(type.displayName(), voyageNumber.value(),
                                location.unlocode()));
    }

    public CargoBookingId cargoBookingId() {
        return cargoBookingId;
    }

    public HandlingType type() {
        return type;
    }

    public Instant completionTime() {
        return completionTime;
    }

    public Location location() {
        return location;
    }

    /** 航海番号。積込・荷降し以外では {@code null}。 */
    public HandlingVoyageNumber voyageNumber() {
        return voyageNumber;
    }

    /** 作業員名。任意。 */
    public String operatorName() {
        return operatorName;
    }

    public long version() {
        return version;
    }
}
