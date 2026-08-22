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

    /** 荷主へ通知した記録（US12-4）。通知していなければ {@code null}。 */
    private final RouteNotification notification;

    /** 発行済みの追跡番号（US14）。未発行なら {@code null}。 */
    private final TrackingNumber trackingNumber;

    private Cargo(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber) {
        this.id = id;
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.status = status;
        this.specification = specification;
        this.routeSpecification = routeSpecification;
        this.itinerary = itinerary;
        this.notification = notification;
        this.trackingNumber = trackingNumber;
    }

    /** 状態と旅程だけを差し替えた写しを作る。遷移のたびに全項目を並べ直さないための道具。 */
    private Cargo with(CargoStatus newStatus, CargoItinerary newItinerary,
            RouteNotification newNotification, TrackingNumber newTrackingNumber) {
        return new Cargo(id, bookingId, shipperId, newStatus, specification, routeSpecification,
                newItinerary, newNotification, newTrackingNumber);
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
                routeSpecification, null, null, null);
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
     *
     * <p><strong>営業へ差し戻された予約（{@link RoutingStatus#CONSULTATION_REQUESTED}）は
     * 再依頼できる。</strong>荷主と条件が決まったら、営業がもう一度引き渡すのが業務の流れである
     * （[ADR-020] 決定 7）。ここを塞ぐと、差し戻した予約が誰の手番でもなくなる。
     */
    public Cargo requestRouting() {
        // IT5 で ROUTE_PROPOSED が増え、この検査は実際に働くようになった（ADR-020 の影響）。
        // 経路が決まった予約への再依頼は、下の RoutingStatus の検査より先にここで落ちる
        if (status.booking() != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException("仮受付の予約だけが経路設計を依頼できます");
        }
        if (status.routing() == RoutingStatus.ROUTING_REQUESTED) {
            throw new IllegalStateException("この予約はすでに経路設計を依頼しています");
        }
        if (status.routing() == RoutingStatus.ROUTED) {
            throw new IllegalStateException("この予約はすでに経路が決まっています");
        }
        return with(new CargoStatus(status.booking(), status.transport(),
                RoutingStatus.ROUTING_REQUESTED), itinerary, notification, trackingNumber);
    }

    /**
     * 経路を割り当てる（US09・[ADR-020]）。
     *
     * <p><strong>引き渡された予約にだけ割り当てられる</strong>（決定 1）。営業が作業中の予約に
     * 経路設計者が手を出せると、引き渡しの記録が「誰の手番か」を表さなくなる。
     * <strong>すでに経路が決まった予約への差し替えは許す</strong>（決定 4）。航海の遅延・欠航は
     * 実際に起こり、そのたびに予約を取り直すのは業務が成り立たない。
     *
     * <p><strong>確定したあとは差し替えられない</strong>（[ADR-021] 決定 3）。差し替えを許すと、
     * 「確定から経路設計へ戻せない」という決定を裏口から破ることになり、荷主が合意した記録が
     * 黙って消える。
     *
     * <p><strong>差し替えると通知の記録は消える。</strong>経路が変わった以上、前の通知は
     * 古い経路についてのものである。残したままだと営業は変わったことに気づかない。
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
        // 確定したあとは差し替えられない（[ADR-021] 決定 3）。
        // 経路の差し替えは RoutingStatus だけを見ていたため、確定済みの予約でも通り、
        // BookingStatus が ROUTE_PROPOSED に戻って**荷主が合意した記録が黙って消えた**。
        // 「確定から戻せない」を裏口から破る形だった（IT6 タスク 0.7 で見つけた）
        if (status.booking() == BookingStatus.CONFIRMED
                || status.booking() == BookingStatus.TRACKING_ISSUED) {
            throw new IllegalStateException(
                    "確定した予約の経路は差し替えられません。変更が必要なら担当者に相談してください");
        }
        if (!routeSpecification.isSatisfiedBy(newItinerary, destinationZone)) {
            throw new IllegalArgumentException(
                    "この旅程は予約の条件（出発地・目的地・到着期限）を満たしていません");
        }
        // 通知の記録は消す（IT6 タスク 0.7）。残したままだと、営業の画面は
        // 「通知しました」と出したまま経路だけが変わる。営業は変わったことに気づかず、
        // 荷主は古い経路の説明を受けたままになる。
        // **気づく手段は手番が営業に戻ること**である（通知の仕組みが無いため、
        // US06・US10 と同じ形で代替する）
        return with(new CargoStatus(BookingStatus.ROUTE_PROPOSED, status.transport(),
                RoutingStatus.ROUTED), newItinerary, null, trackingNumber);
    }

    /** 割り当てられた旅程。まだ経路が決まっていなければ空を返す。 */
    public Optional<CargoItinerary> itinerary() {
        return Optional.ofNullable(itinerary);
    }

    /**
     * 荷主へ経路を通知する（US12・[ADR-021] 決定 1・決定 2）。
     *
     * <p><strong>いま経路が決まっている予約だけ</strong>を通知できる。経路設計へ戻した予約
     * （{@link RoutingStatus#ROUTING_REQUESTED}）は、経路設計者が組み直すまで通知できない。
     *
     * <p><strong>もう一度通知できる</strong>（決定 2）。返事が無い・連絡先を間違えた・内容を
     * 補足したい、はいずれも実務で起きる。塞ぐと営業は経路設計へ戻して割り当て直すという
     * 遠回りをするか、システムの外で連絡して記録が残らなくなる。記録は最新で上書きする。
     *
     * <p><strong>経路の状態は動かさない。</strong>経路設計は終わっており、通知は予約の
     * ライフサイクル側の出来事である。
     */
    public Cargo notifyShipper(java.time.Instant notifiedAt, String notifiedBy) {
        // **いま経路が決まっていること**を見る。BookingStatus だけを見ると、経路設計へ
        // 戻した予約（BookingStatus は ROUTE_PROPOSED に戻る）を、経路設計者が触る前に
        // 同じ経路のまま通知できてしまう。荷主が「この経路は困る」と言って戻したものを
        // 通知済 → 確定にでき、荷役はその予定で動き、荷主は違う話を聞くことになる
        if (status.routing() != RoutingStatus.ROUTED) {
            throw new IllegalStateException("経路が決まった予約だけを荷主へ通知できます");
        }
        if (status.booking() != BookingStatus.ROUTE_PROPOSED
                && status.booking() != BookingStatus.ROUTE_NOTIFIED) {
            throw new IllegalStateException("経路が決まった予約だけを荷主へ通知できます");
        }
        return with(new CargoStatus(BookingStatus.ROUTE_NOTIFIED, status.transport(),
                status.routing()), itinerary, RouteNotification.of(notifiedAt, notifiedBy),
                trackingNumber);
    }

    /** 荷主へ通知した記録。通知していなければ空を返す。 */
    public java.util.Optional<RouteNotification> routeNotification() {
        return Optional.ofNullable(notification);
    }

    /**
     * 荷主の合意を得て確定する（US13-2・[ADR-021] 決定 1）。
     *
     * <p><strong>通知していない予約は確定できない。</strong>確定は「荷主の合意を得た」という
     * 業務上の事実であり、提示していない条件で合意は成り立たない。
     */
    public Cargo confirm() {
        if (status.booking() != BookingStatus.ROUTE_NOTIFIED) {
            throw new IllegalStateException("荷主へ通知した予約だけを確定できます");
        }
        return with(new CargoStatus(BookingStatus.CONFIRMED, status.transport(), status.routing()),
                itinerary, notification, trackingNumber);
    }

    /**
     * 荷主が変更を希望したので経路設計へ戻す（US13-4・[ADR-021] 決定 3・決定 4）。
     *
     * <p><strong>経路の状態も戻す。</strong>{@code BookingStatus} だけ戻しても経路設計者の
     * 作業待ちに現れず、荷主が変更を希望したことが誰にも伝わらない。
     *
     * <p><strong>旅程は消さない。</strong>見直しの起点になる（どこが気に入られなかったかを、
     * いまの経路を見ながら話す）。
     *
     * <p><strong>確定したあとは戻せない</strong>（決定 3）。確定は追跡番号の発行と荷役の起点で
     * あり、戻せるようにすると荷役の担当者と荷主が別の予定を見る。確定後に変更が要るなら、
     * それはキャンセル（US30）か経路の差し替え（[ADR-020] 決定 4）である。
     */
    public Cargo returnToRouting() {
        if (status.booking() != BookingStatus.ROUTE_NOTIFIED) {
            throw new IllegalStateException("荷主へ通知した予約だけを経路設計へ戻せます");
        }
        return with(new CargoStatus(BookingStatus.ROUTE_PROPOSED, status.transport(),
                RoutingStatus.ROUTING_REQUESTED), itinerary, notification, trackingNumber);
    }

    /**
     * 追跡番号を発行する（US14-1・US14-3）。
     *
     * <p>確定した予約にだけ発行できる。二重には発行しない——番号が変わると、荷主に伝えた
     * 番号で追えなくなる。
     *
     * <p><strong>番号はここで組み立てない</strong>（[ADR-011] と同じ形）。採番は永続化の経路が
     * 行い、集約は受け取って持つだけである。
     */
    public Cargo issueTrackingNumber(TrackingNumber issued) {
        if (issued == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (status.booking() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("確定した予約にだけ追跡番号を発行できます");
        }
        if (trackingNumber != null) {
            throw new IllegalStateException("この予約はすでに追跡番号を発行しています");
        }
        // 貨物はまだ動いていない。受領待ちのままであることを明示する（US14-3）
        return with(new CargoStatus(BookingStatus.TRACKING_ISSUED, TransportStatus.NOT_RECEIVED,
                status.routing()), itinerary, notification, issued);
    }

    /** 発行済みの追跡番号。未発行なら空を返す。 */
    public java.util.Optional<TrackingNumber> trackingNumber() {
        return Optional.ofNullable(trackingNumber);
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
        return with(new CargoStatus(status.booking(), status.transport(),
                RoutingStatus.CONSULTATION_REQUESTED), itinerary, notification, trackingNumber);
    }

    /**
     * 日程を訂正する（US06 の訂正・IT6 タスク 0.11）。
     *
     * <p>条件協議の結果が「期限を延ばす」だったとき、<strong>予約を直せないと再依頼しても
     * 同じ結果になる</strong>。営業は予約を作り直すことになり、予約番号が変わって他サービスの
     * 参照が外れる（[ADR-011]）。
     *
     * <p><strong>経路設計者の作業中は直せない。</strong>組んでいる最中に条件が変わると、
     * 出来上がった経路が条件を満たさなくなる。直したいなら先に協議へ戻す（US10）。
     * 経路が決まったあとも直せない——先に見直しが要る（[ADR-020] 決定 4）。
     *
     * <p>直せるのは<strong>日程だけ</strong>である。出発地・目的地・貨物の仕様を変えるなら、
     * それは別の予約である。
     */
    public Cargo reviseSchedule(java.time.LocalDate departureDate,
            java.time.LocalDate arrivalDeadline, ZoneId destinationZone, java.time.Clock clock) {
        if (status.routing() != RoutingStatus.NOT_ROUTED
                && status.routing() != RoutingStatus.CONSULTATION_REQUESTED) {
            throw new IllegalStateException(
                    "経路設計に引き渡す前か、営業へ戻された予約だけを直せます");
        }
        return new Cargo(id, bookingId, shipperId, status, specification,
                routeSpecification.withSchedule(departureDate, arrivalDeadline, destinationZone,
                        clock),
                itinerary, notification, trackingNumber);
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
        // 判定は RoutingStatus が持つ。ここで数え上げ直すと、範囲を広げたときに
        // 片方だけが古いままになる（ADR-020 後日談で一本化した形。IT6 でここが
        // 数え上げに戻っていたのを直した）
        return status.routing().visibleToRoutingPlanner();
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
        return restore(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, null, null);
    }

    /**
     * 通知の記録と追跡番号まで伴って復元する。ここでは検査しない（[ADR-012]）。
     *
     * <p><strong>不変条件（`ROUTE_NOTIFIED` 以降なら通知の記録がある）をここで検査しない。</strong>
     * 列が無かったころの行が読めなくなる。守るのは新しく受け入れるときだけでよい。
     */
    public static Cargo restore(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber) {
        return new Cargo(id, bookingId, shipperId, status, specification, routeSpecification,
                itinerary, notification, trackingNumber);
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
