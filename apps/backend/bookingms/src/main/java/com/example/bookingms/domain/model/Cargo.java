package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.time.ZoneId;
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
    /** 割り当てられた旅程。まだ経路が決まっていなければ持たない。 */
    private final CargoItinerary itinerary;

    private Cargo(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary) {
        this.id = id;
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.status = status;
        this.specification = specification;
        this.routeSpecification = routeSpecification;
        this.itinerary = itinerary;
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

        return new Cargo(null, null, shipperId, CargoStatus.preliminary(), specification,
                routeSpecification, null);
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

    /**
     * 経路設計を依頼する（US06）。
     *
     * <p>仮受付の予約からしか依頼できない。確定済み・キャンセル済みの予約を経路設計の
     * 待ち行列に混ぜると、経路設計者はもう作業の要らない予約に時間を使う。
     *
     * <p>依頼済みの予約に再依頼はできない。二重に依頼しても待ち行列に同じ予約が並ぶだけで、
     * 経路設計者から見ると「同じ仕事が 2 件ある」ように見える。
     */
    public Cargo requestRouting() {
        // BookingStatus は IT3 時点で PRELIMINARY だけであり、この検査はまだ働く場面が無い。
        // 破って赤にするテストも書けない。US11（予約確定）・UC22（キャンセル）で状態が増えたときに、
        // 確定済み・キャンセル済みを弾くテストと対にする
        if (status.booking() != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException("仮受付の予約だけが経路設計を依頼できます");
        }
        if (status.routing() == RoutingStatus.ROUTING_REQUESTED) {
            throw new IllegalStateException("この予約はすでに経路設計を依頼しています");
        }
        if (status.routing() == RoutingStatus.ROUTED) {
            throw new IllegalStateException("この予約はすでに経路が決まっています");
        }
        return new Cargo(id, bookingId, shipperId,
                new CargoStatus(status.booking(), status.transport(), RoutingStatus.ROUTING_REQUESTED),
                specification, routeSpecification, itinerary);
    }

    /**
     * 経路を割り当てる（US09・[ADR-020]）。
     *
     * <p><strong>引き渡された予約にだけ割り当てられる</strong>（決定 1）。営業が作業中の予約に
     * 経路設計者が手を出せると、引き渡しの記録が「誰の手番か」を表さなくなる。
     * <strong>すでに経路が決まった予約への差し替えは許す</strong>（決定 4）。航海の遅延・欠航は
     * 実際に起こり、そのたびに予約を取り直すのは業務が成り立たない。
     *
     * <p><strong>旅程が予約の要件を満たさなければ断る</strong>（決定 5）。端点が違えば荷主は
     * 貨物を渡せない場所で待ち、期限を過ぎれば約束を破ることが確定した状態で予約が進む。
     * 判定は {@link RouteSpecification#isSatisfiedBy} に置き、画面と集約で別々に書かない。
     *
     * @param destinationZone 目的地の業務タイムゾーン。到着期限の「当日」を決めるのに使う
     */
    public Cargo assignItinerary(CargoItinerary newItinerary, ZoneId destinationZone) {
        if (newItinerary == null) {
            throw new IllegalArgumentException("割り当てる旅程は必須です");
        }
        if (status.routing() != RoutingStatus.ROUTING_REQUESTED
                && status.routing() != RoutingStatus.ROUTED) {
            throw new IllegalStateException("経路設計を依頼された予約にだけ経路を割り当てられます");
        }
        if (!routeSpecification.isSatisfiedBy(newItinerary, destinationZone)) {
            throw new IllegalArgumentException(
                    "この旅程は予約の条件（出発地・目的地・到着期限）を満たしていません");
        }
        return new Cargo(id, bookingId, shipperId,
                new CargoStatus(BookingStatus.ROUTE_PROPOSED, status.transport(),
                        RoutingStatus.ROUTED),
                specification, routeSpecification, newItinerary);
    }

    /** 割り当てられた旅程。まだ経路が決まっていなければ空を返す。 */
    public Optional<CargoItinerary> itinerary() {
        return Optional.ofNullable(itinerary);
    }

    /**
     * 条件では経路が組めないことを、営業へ差し戻す（US10・[ADR-020] 決定 7）。
     *
     * <p>引き渡された予約にだけ行える。<strong>経路が決まった予約には行えない</strong>
     * （決まっているのに協議を頼むのは、差し替えるべき場面である）。
     */
    public Cargo requestConsultation() {
        if (status.routing() != RoutingStatus.ROUTING_REQUESTED) {
            throw new IllegalStateException(
                    "経路設計を依頼された予約だけが、条件の協議を営業へ戻せます");
        }
        return new Cargo(id, bookingId, shipperId,
                new CargoStatus(status.booking(), status.transport(),
                        RoutingStatus.CONSULTATION_REQUESTED),
                specification, routeSpecification, itinerary);
    }

    /** 経路設計の依頼を待っているか。判定を呼び出し側に散らかさない。 */
    public boolean awaitingRouting() {
        return status.routing() == RoutingStatus.ROUTING_REQUESTED;
    }

    /**
     * 経路設計者に開いてよい予約か（[ADR-015]）。
     *
     * <p>経路設計者の仕事は「依頼された予約に経路を組む」ことであり、営業が作業中の予約は
     * 対象ではない。一覧と詳細で別々に判断すると、片方を絞ってももう片方から同じ範囲が
     * 読める。**判定はここ 1 箇所に置き、入口はこれを呼ぶ。**
     */
    public boolean visibleToRoutingPlanner() {
        // 経路が決まった予約も開く（ADR-020 決定 3）。割り当てた直後に自分が開けなくなると、
        // 確定画面にも旅程にも辿り着けない
        // 協議を戻した予約も開いたままにする。差し戻した本人が確認できなくなると、
        // 営業と話したあとに続きができない
        return awaitingRouting()
                || status.routing() == RoutingStatus.ROUTED
                || status.routing() == RoutingStatus.CONSULTATION_REQUESTED;
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification) {
        return restore(id, bookingId, shipperId, status, specification, routeSpecification, null);
    }

    /** 旅程を伴って復元する。ここでは検査しない。 */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary) {
        return new Cargo(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary);
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
