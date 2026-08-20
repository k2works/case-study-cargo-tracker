package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 貨物予約。IT2 で作れるのは仮受付（{@link BookingStatus#PRELIMINARY}）までで、
 * 経路・配送状況・キャンセルは IT3 以降で扱う。
 *
 * <p>予約番号は永続化の経路（DB シーケンス）で採番する。集約側で組み立てると、
 * シーケンスと衝突した番号を発行できてしまう（ADR-011）。
 */
public final class Cargo {

    private final Long id;
    private final BookingId bookingId;
    private final Long shipperId;
    private final CargoStatus status;
    private final CargoSpecification specification;
    private final RouteSpecification routeSpecification;

    private Cargo(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification) {
        this.id = id;
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.status = status;
        this.specification = specification;
        this.routeSpecification = routeSpecification;
    }

    /**
     * 予約を受け付ける。ここでだけ入力を検査する。
     *
     * <p>状態は空欄にせず、意味のある初期値を置く（ADR-009）。列を nullable にして後から
     * NOT NULL を足すと、IT2 で入った行が読めなくなる。
     */
    public static Cargo book(Long shipperId, CargoSpecification specification,
            RouteSpecification routeSpecification) {
        if (shipperId == null) {
            throw new IllegalArgumentException("荷主は必須です");
        }
        if (routeSpecification == null) {
            throw new IllegalArgumentException("輸送条件は必須です");
        }
        requireValidSpecification(specification);

        return new Cargo(
                null, null, shipperId, CargoStatus.preliminary(), specification, routeSpecification);
    }

    /**
     * 貨物仕様の不変条件。
     *
     * <p>付け忘れ（危険物なのに申告が無い）と同じく、付けすぎ（一般貨物に温度条件がある）も
     * 誤りとして扱う。付けすぎを通すと、経路設計（IT3）が「温度条件のある一般貨物」を
     * どう扱うか判断できない。
     */
    private static void requireValidSpecification(CargoSpecification specification) {
        if (specification == null || specification.type() == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (specification.weightKg() == null || specification.weightKg().signum() <= 0) {
            throw new IllegalArgumentException(
                    "重量は 0 より大きい値で指定してください: " + specification.weightKg());
        }
        if (specification.quantity() != null && specification.quantity() <= 0) {
            throw new IllegalArgumentException("個数は 1 以上で指定してください: " + specification.quantity());
        }

        boolean hazardous = specification.type() == CargoType.HAZARDOUS;
        boolean refrigerated = specification.type() == CargoType.REFRIGERATED;

        if (hazardous && specification.hazardousDeclaration() == null) {
            throw new IllegalArgumentException("危険物には危険物申告が必要です");
        }
        if (!hazardous && specification.hazardousDeclaration() != null) {
            throw new IllegalArgumentException("危険物申告は危険物にだけ設定できます");
        }
        if (refrigerated && specification.temperatureRequirement() == null) {
            throw new IllegalArgumentException("冷凍・冷蔵貨物には保管温度の条件が必要です");
        }
        if (!refrigerated && specification.temperatureRequirement() != null) {
            throw new IllegalArgumentException("保管温度の条件は冷凍・冷蔵貨物にだけ設定できます");
        }
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification) {
        return new Cargo(id, bookingId, shipperId, status, specification, routeSpecification);
    }

    public Long id() {
        return id;
    }

    /** 予約番号。採番前（登録直後）は空。 */
    public Optional<BookingId> bookingId() {
        return Optional.ofNullable(bookingId);
    }

    public Long shipperId() {
        return shipperId;
    }

    public CargoStatus status() {
        return status;
    }

    public BookingStatus bookingStatus() {
        return status.booking();
    }

    public TransportStatus transportStatus() {
        return status.transport();
    }

    public RoutingStatus routingStatus() {
        return status.routing();
    }

    public CargoSpecification specification() {
        return specification;
    }

    public RouteSpecification routeSpecification() {
        return routeSpecification;
    }

    public CargoType type() {
        return specification.type();
    }

    public BigDecimal weightKg() {
        return specification.weightKg();
    }

    /** 危険物申告を必要とするか。種別の比較を呼び出し側に散らかさない。 */
    public boolean requiresHazardousDeclaration() {
        return specification.type() == CargoType.HAZARDOUS;
    }

    /** 温度条件を必要とするか。 */
    public boolean requiresTemperatureRequirement() {
        return specification.type() == CargoType.REFRIGERATED;
    }

    public Optional<HazardousDeclaration> hazardousDeclaration() {
        return Optional.ofNullable(specification.hazardousDeclaration());
    }

    public Optional<TemperatureRequirement> temperatureRequirement() {
        return Optional.ofNullable(specification.temperatureRequirement());
    }
}
