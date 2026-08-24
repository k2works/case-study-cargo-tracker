package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 貨物予約（集約ルート）。
 *
 * <p>仮受付から経路の割り当て・荷主への通知・確定・追跡番号の発行を経て、荷役の記録で
 * 輸送中・配送完了まで進む。キャンセルは輸送開始前なら即時、輸送中は追跡管理者の承認を
 * 経て確定する（US30）。<strong>精算（`SETTLED`）はまだ無い</strong>——US23（IT12）である。
 *
 * <p>予約番号は永続化の経路（DB シーケンス）で採番する。集約側で組み立てると、
 * シーケンスと衝突した番号を発行できてしまう（[ADR-011]）。
 *
 * <p><strong>可否の判定は {@link CargoTransitionPolicy} が持つ。</strong>集約に散らすと、
 * 状態を足したときに直す場所が増える。復元は {@link CargoRestoration}、貨物仕様の
 * 不変条件は {@code CargoSpecificationRules} にある——どちらも責務が違う。
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

    /**
     * 最後に荷役があった地点（[ADR-025] 決定 4）。まだ無ければ null。
     *
     * <p><strong>陸揚げ地の候補「現在地の港」はこれを使う。</strong>trackingms へ引かない
     * ——現在地の一次情報は荷役にあり、trackingms もそれを購読して得ている。
     * 2 ホップ先から取りに行く形にすると、同じ事実の伝聞が 1 段増える。
     */
    private final String lastHandlingLocationUnLocode;

    /** 最後の荷役の日時。まだ無ければ null。 */
    private final Instant lastHandlingAt;

    // S107（引数が多い）: 復元は永続化された行の写しであり、列数がそのまま現れる。
    // まとめると復元の意味が薄れる（テスト戦略の Quality Gate 例外表に記載）
    @SuppressWarnings("java:S107")
    private Cargo(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber) {
        this(id, bookingId, shipperId, status, specification, routeSpecification, itinerary,
                notification, trackingNumber, null, null);
    }

    @SuppressWarnings("java:S107")
    Cargo(Long id, BookingId bookingId, Long shipperId, CargoStatus status,
            CargoSpecification specification, RouteSpecification routeSpecification,
            CargoItinerary itinerary, RouteNotification notification,
            TrackingNumber trackingNumber, String lastHandlingLocationUnLocode,
            Instant lastHandlingAt) {
        this.lastHandlingLocationUnLocode = lastHandlingLocationUnLocode;
        this.lastHandlingAt = lastHandlingAt;
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
                newItinerary, newNotification, newTrackingNumber, lastHandlingLocationUnLocode,
                lastHandlingAt);
    }

    /** 荷役の記録を反映した写しを作る。状態と最後の荷役地点が同時に動く。 */
    private Cargo withHandling(CargoStatus newStatus, String locationUnLocode, Instant at) {
        return new Cargo(id, bookingId, shipperId, newStatus, specification, routeSpecification,
                itinerary, notification, trackingNumber, locationUnLocode, at);
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
        CargoSpecificationRules.requireValid(specification);

        return new Cargo(null, null, shipperId, CargoStatus.preliminary(), specification,
                routeSpecification, null, null, null);
    }

    /**
     * いま経路設計を依頼できるか。
     *
     * <p><strong>可否は集約が答える。</strong>画面やモックが状態名を見比べて同じ判断を
     * 組み立てると、規則が 3 か所に分かれ、片方だけ直る形になる（IT6 ふりかえり Try 5）。
     */
    public boolean canRequestRouting() {
        return transitions().reasonCannotRequestRouting().isEmpty();
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
        // 理由ごとに文言を分ける。断りの文言は「何を直せばよいか」を伝えるものであり、
        // 1 つにまとめると利用者は次に何をすればよいか分からない
        transitions().reasonCannotRequestRouting().ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
        return with(new CargoStatus(status.booking(), status.transport(),
                RoutingStatus.ROUTING_REQUESTED), itinerary, notification, trackingNumber);
    }

    /** いま経路を割り当てられるか（旅程そのものの妥当性はここでは見ない）。 */
    public boolean canAssignItinerary() {
        return transitions().reasonCannotAssignItinerary().isEmpty();
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
        // 確定したあとは差し替えられない（[ADR-021] 決定 3）。
        // 経路の差し替えは RoutingStatus だけを見ていたため、確定済みの予約でも通り、
        // BookingStatus が ROUTE_PROPOSED に戻って**荷主が合意した記録が黙って消えた**。
        // 「確定から戻せない」を裏口から破る形だった（IT6 タスク 0.7 で見つけた）
        transitions().reasonCannotAssignItinerary().ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
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

    /** いま荷主へ通知できるか。 */
    public boolean canNotifyShipper() {
        return status.routing() == RoutingStatus.ROUTED
                && (status.booking() == BookingStatus.ROUTE_PROPOSED
                        || status.booking() == BookingStatus.ROUTE_NOTIFIED);
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
        if (!canNotifyShipper()) {
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

    /** いま確定できるか。 */
    public boolean canConfirm() {
        return status.booking() == BookingStatus.ROUTE_NOTIFIED;
    }

    /**
     * 荷主の合意を得て確定する（US13-2・[ADR-021] 決定 1）。
     *
     * <p><strong>通知していない予約は確定できない。</strong>確定は「荷主の合意を得た」という
     * 業務上の事実であり、提示していない条件で合意は成り立たない。
     */
    public Cargo confirm() {
        if (!canConfirm()) {
            throw new IllegalStateException("荷主へ通知した予約だけを確定できます");
        }
        return with(new CargoStatus(BookingStatus.CONFIRMED, status.transport(), status.routing()),
                itinerary, notification, trackingNumber);
    }

    /** いま経路設計へ戻せるか。 */
    public boolean canReturnToRouting() {
        return status.booking() == BookingStatus.ROUTE_NOTIFIED;
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
        if (!canReturnToRouting()) {
            throw new IllegalStateException("荷主へ通知した予約だけを経路設計へ戻せます");
        }
        return with(new CargoStatus(BookingStatus.ROUTE_PROPOSED, status.transport(),
                RoutingStatus.ROUTING_REQUESTED), itinerary, notification, trackingNumber);
    }

    /** いま追跡番号を発行できるか。 */
    public boolean canIssueTrackingNumber() {
        return transitions().reasonCannotIssueTrackingNumber().isEmpty();
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
        transitions().reasonCannotIssueTrackingNumber().ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
        // 貨物はまだ動いていない。受領待ちのままであることを明示する（US14-3）
        return with(new CargoStatus(BookingStatus.TRACKING_ISSUED, TransportStatus.NOT_RECEIVED,
                status.routing()), itinerary, notification, issued);
    }

    /**
     * 荷役の記録に応じて状態を進める（[ADR-025] 決定 1）。
     *
     * <p><strong>bookingms は自分では輸送中を知らない。</strong>荷役の記録が一次情報で
     * あり、{@code HandlingActivityRegisteredEvent} を購読して進める。
     *
     * <p>どこまで進むか・進めてよいかの判定は {@link CargoTransitionPolicy} が持つ。
     */
    public Cargo afterHandling(String handlingType, String locationUnLocode, Instant at) {
        return transitions().bookingStatusAfterHandling(handlingType)
                .map(advanced -> withHandling(
                        new CargoStatus(advanced, status.transport(), status.routing()),
                        locationUnLocode, at))
                .orElse(this);
    }

    /**
     * 輸送中か（US30-3）。<strong>キャンセルに承認が要るかを、集約が答える。</strong>
     *
     * <p>画面やユースケースが状態名を見比べると、規則が 2 か所に分かれる。
     */
    public boolean isInTransit() {
        return status.booking() == BookingStatus.IN_TRANSIT;
    }

    /** いまキャンセルを申請できるか（US30-1）。判定は {@link CargoTransitionPolicy} が持つ。 */
    public boolean canRequestCancellation() {
        return transitions().reasonCannotCancel().isEmpty();
    }

    /**
     * キャンセルを確定する（US30）。
     *
     * <p><strong>ここが {@code CANCELLED} へ動かす唯一の場所である</strong>
     * （{@code BookingStatusTest#cancelsOnlyThroughTheAggregate}）。別の場所から
     * 直接動かせると、輸送中の貨物が承認を経ずに止まる。
     *
     * <p><strong>承認が要るかどうかはここでは判断しない。</strong>それを決めるのは
     * 申請のユースケースであり、集約が答えるのは {@link #isInTransit()} までである。
     *
     * @throws IllegalStateException 配送完了・キャンセル済みのとき
     */
    public Cargo cancel() {
        transitions().reasonCannotCancel().ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
        return with(new CargoStatus(BookingStatus.CANCELLED, status.transport(), status.routing()),
                itinerary, notification, trackingNumber);
    }

    /** 最後に荷役があった地点（[ADR-025] 決定 4）。陸揚げ地の候補「現在地の港」に使う。 */
    public Optional<String> lastHandlingLocation() {
        return Optional.ofNullable(lastHandlingLocationUnLocode);
    }

    /** 最後の荷役の日時。 */
    public Optional<Instant> lastHandlingAt() {
        return Optional.ofNullable(lastHandlingAt);
    }

    /** 発行済みの追跡番号。未発行なら空を返す。 */
    public java.util.Optional<TrackingNumber> trackingNumber() {
        return Optional.ofNullable(trackingNumber);
    }

    /** いま条件の協議を営業へ戻せるか。 */
    public boolean canRequestConsultation() {
        return status.routing() == RoutingStatus.ROUTING_REQUESTED;
    }

    /**
     * 条件では経路が組めないことを、営業へ差し戻す（US10・[ADR-020] 決定 7）。
     *
     * <p>引き渡された予約にだけ行える。<strong>経路が決まった予約には行えない</strong>
     * （決まっているのに協議を頼むのは、差し替えるべき場面である）。
     */
    public Cargo requestConsultation() {
        if (!canRequestConsultation()) {
            throw new IllegalStateException(
                    "経路設計を依頼された予約だけが、条件の協議を営業へ戻せます");
        }
        return with(new CargoStatus(status.booking(), status.transport(),
                RoutingStatus.CONSULTATION_REQUESTED), itinerary, notification, trackingNumber);
    }

    /** いま日程を直せるか。 */
    public boolean canReviseSchedule() {
        return status.routing() == RoutingStatus.NOT_ROUTED
                || status.routing() == RoutingStatus.CONSULTATION_REQUESTED;
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
        if (!canReviseSchedule()) {
            throw new IllegalStateException(
                    "経路設計に引き渡す前か、営業へ戻された予約だけを直せます");
        }
        return new Cargo(id, bookingId, shipperId, status, specification,
                routeSpecification.withSchedule(departureDate, arrivalDeadline, destinationZone,
                        clock),
                itinerary, notification, trackingNumber);
    }

    /** いまの状態で何ができるかを答える方針（[ADR-021]）。 */
    private CargoTransitionPolicy transitions() {
        return new CargoTransitionPolicy(status, trackingNumber != null);
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
