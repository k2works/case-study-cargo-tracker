package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.ShipperId;

/**
 * 貨物。Booking Context の集約ルート。
 *
 * <p>状態遷移の規則は {@link BookingStatus} が持ち、本クラスは
 * 「どのコマンドをいつ実行してよいか」を集約の文脈で判断する。
 *
 * <p><strong>Setter を持たない。</strong> 状態を変える手段は業務のことばで名づけた
 * 振る舞い（{@link #cancel()} 等）に限る。Setter を生やすと、不変条件を通らずに
 * 状態を書き換える経路ができる。
 */
public class Cargo {

    private final BookingId bookingId;
    private final ShipperId shipperId;
    private final CargoSpecification cargoSpecification;
    private final RouteSpecification routeSpecification;
    private final long version;

    /**
     * 予約がどこまで進んだか（状態・経路・追跡番号）。
     *
     * <p><strong>経路は予約状態とは別に動く。</strong> 経路を確定しても
     * {@code BookingStatus} は変わらない（遷移表 3）。
     */
    private CargoProgress progress;

    /**
     * 誤配を検知した荷役の写し（US28 / C28）。
     *
     * <p><strong>結果整合の写しである。</strong> Handling のテーブルを
     * 読みに行かないために持つ。
     */
    private MisrouteDetection misrouteDetection;

    /**
     * 引取確認コード（US35）。確定前は {@code null}。
     *
     * <p><strong>追跡番号とは別の値である。</strong> 追跡番号は取引先へ転送される
     * 合鍵であり、それを知っているだけで引き取れてはならない。
     */
    private ClaimCode claimCode;

    /**
     * 荷受人（US16）。<strong>予約の時点では未確定でありうる。</strong>
     *
     * <p>国際輸送では荷受人が後から決まることがある。必須にすると、
     * 荷受人が決まるまで予約を登録できなくなる。
     */
    private Consignee consignee;

    /**
     * 引取が済んだ日時（IT13 レビュー C1）。引取前・旧い行では {@code null}。
     *
     * <p><strong>荷役の記録から導出しない。</strong> 導出すると、荷役の記録が
     * 訂正・削除されたときに請求済みの引取日が黙って動く。
     */
    private java.time.Instant claimedAt;

    private Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            CargoProgress progress,
            long version) {
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.cargoSpecification = cargoSpecification;
        this.routeSpecification = routeSpecification;
        this.progress = progress;
        this.version = version;
    }

    /**
     * 予約を登録する（遷移表 #1）。
     *
     * <p>ビジネスルール 1: 貨物は必ず BookingId・ShipperId・CargoType を持つ。
     */
    public static Cargo book(BookCargoCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("予約コマンドは必須です");
        }
        if (command.shipperId() == null) {
            throw new IllegalArgumentException("荷主は必須です");
        }
        if (command.cargoSpecification() == null) {
            throw new IllegalArgumentException("貨物仕様は必須です");
        }
        if (command.routeSpecification() == null) {
            throw new IllegalArgumentException("出発地・目的地・到着期限は必須です");
        }
        return new Cargo(
                BookingId.generate(),
                command.shipperId(),
                command.cargoSpecification(),
                command.routeSpecification(),
                // 新規の予約は経路が割り当てられておらず、追跡番号も持たない
                CargoProgress.initial(),
                0L);
    }

    /**
     * 永続化された状態から復元する。
     *
     * <p><strong>状態は保存された値をそのまま使い、履歴から導出しない。</strong>
     * 導出すると、ユニットテストが緑のままでも別リクエストで状態が巻き戻る。
     */
    public static Cargo reconstruct(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            BookingStatus bookingStatus,
            CargoRouting routing,
            long version) {
        return reconstruct(bookingId, shipperId, cargoSpecification, routeSpecification,
                new CargoProgress(bookingStatus, routing, null), version);
    }

    /** 追跡番号を含めて復元する（US14 以降）。 */
    public static Cargo reconstruct(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            CargoProgress progress,
            long version) {
        return new Cargo(bookingId, shipperId, cargoSpecification, routeSpecification,
                progress, version);
    }

    /** 荷受人を含めて復元する（US16 以降）。 */
    public static Cargo reconstruct(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            CargoProgress progress,
            Consignee consignee,
            long version) {
        Cargo cargo = new Cargo(bookingId, shipperId, cargoSpecification,
                routeSpecification, progress, version);
        cargo.consignee = consignee;
        return cargo;
    }

    /**
     * 引取確認コードを載せて返す（US35）。
     *
     * <p><strong>復元の引数を増やさない</strong>（{@link #withMisrouteDetection} と同じ形）。
     *
     * <p><strong>コードが無くても復元は成り立つ。</strong> 列が無かったころに
     * 確定した予約は値を持たない。拒むとその予約の画面ごと 500 になる。
     */
    public Cargo withClaimCode(ClaimCode code) {
        this.claimCode = code;
        return this;
    }

    /** 引取確認コード。確定前・旧い行では {@code null}。 */
    public ClaimCode claimCode() {
        return claimCode;
    }

    /**
     * 誤配の写しを載せて返す（US28 / C28）。
     *
     * <p><strong>復元の引数を増やさない。</strong> 引数が増え続ける復元は、
     * 呼び出し側が順番を間違えても型で気づけなくなる（実際に 8 個目で
     * Checkstyle が止めた）。<strong>制限に当たったのは合図である。</strong>
     *
     * <p><strong>写しが無くても復元は成り立つ。</strong> 列が無かったころに誤配に
     * なった貨物は値を持たない。拒むとその予約の画面ごと 500 になる。
     */
    public Cargo withMisrouteDetection(MisrouteDetection detection) {
        this.misrouteDetection = detection;
        return this;
    }

    /**
     * 荷受人を登録する（US16）。
     *
     * <p><strong>いつでも登録・訂正できる。</strong> 予約状態で制限しない。
     * 荷受人は輸送の直前まで変わりうる（転売・配送先の変更）。
     *
     * <p>ただし<strong>引き渡し済み以降は変えない</strong>。引き渡した後に
     * 荷受人を書き換えると、誰に渡したかの記録が後から作り変えられる。
     *
     * @throws IllegalStateException 引き渡し済み以降のとき
     */
    public void registerConsignee(Consignee newConsignee) {
        if (newConsignee == null) {
            throw new IllegalArgumentException("荷受人は必須です");
        }
        if (isDelivered()) {
            throw new IllegalStateException(
                    "引き渡し済みの予約の荷受人は変更できません");
        }
        this.consignee = newConsignee;
    }

    /** 荷受人。未登録なら {@code null}。 */
    public Consignee consignee() {
        return consignee;
    }

    /** 引き渡し済み以降か（荷受人を変更できない状態）。 */
    public boolean isDelivered() {
        return progress.status().isDeliveredOrLater();
    }

    /**
     * 経路設計者に引き渡せるか（遷移表 #2。US06）。
     *
     * <p>画面のボタン出し分けは本述語をそのまま呼ぶ。**引き渡し済みの予約に
     * 「引き渡す」ボタンが出ていると、二重に依頼が飛ぶ。**
     */
    public boolean canAssignToRouting() {
        return progress.status().canTransitionBy(BookingCommandType.ASSIGN_TO_ROUTING);
    }

    /**
     * 経路設計者に引き渡す（US06）。
     *
     * @throws InvalidBookingStatusTransitionException 引き渡せない状態のとき
     */
    public void assignToRouting() {
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.ASSIGN_TO_ROUTING));
    }

    /**
     * キャンセルできるか（遷移表 #9 / #10）。
     *
     * <p>画面のボタン出し分けは本述語をそのまま呼ぶ。
     */
    public boolean canCancel() {
        return progress.status().canTransitionBy(BookingCommandType.CANCEL_BOOKING);
    }

    /**
     * 確定した経路（旅程）を割り当てる（US09 / US11。遷移表 #3）。
     *
     * <p><strong>予約状態は変えない。</strong> 動くのは経路状態だけである。
     *
     * <p>旅程の端点は予約の出発地・目的地と一致しなければならない。
     * <strong>違う旅程を割り当てると、荷主が頼んだ場所と違う場所へ運ぶことになる。</strong>
     *
     * @throws IllegalStateException    経路割り当ての対象でない状態のとき
     * @throws IllegalArgumentException 旅程の端点が予約と一致しないとき
     */
    public void assignItinerary(CargoItinerary itinerary) {
        if (itinerary == null) {
            throw new IllegalArgumentException("旅程は必須です");
        }
        if (progress.status() != BookingStatus.ROUTE_PROPOSED) {
            throw new IllegalStateException(
                    "経路を割り当てられる状態ではありません: " + progress.status().displayName());
        }
        if (!itinerary.origin().equals(routeSpecification.origin())) {
            throw new IllegalArgumentException(
                    "旅程の出発地が予約と一致しません: 予約 %s / 旅程 %s".formatted(
                            routeSpecification.origin().unlocode(),
                            itinerary.origin().unlocode()));
        }
        if (!itinerary.destination().equals(routeSpecification.destination())) {
            throw new IllegalArgumentException(
                    "旅程の目的地が予約と一致しません: 予約 %s / 旅程 %s".formatted(
                            routeSpecification.destination().unlocode(),
                            itinerary.destination().unlocode()));
        }
        this.progress = progress.withRouting(CargoRouting.routed(itinerary));
    }

    /**
     * 予約を確定できるか（遷移表 #4。US13）。
     *
     * <p><strong>事前条件は状態だけでは足りない。</strong> 経路が割り当てられて
     * いない予約を確定すると、運ぶ道筋の無い予約に荷主の同意が付く。
     * 画面のボタン出し分けは本述語をそのまま呼ぶ。
     */
    public boolean canConfirm() {
        return CargoProgress.confirmable(progress.status(), progress.routing().status());
    }

    /**
     * 予約を確定する（US13。遷移表 #4）。
     *
     * @throws IllegalStateException                   経路が割り当てられていないとき
     * @throws InvalidBookingStatusTransitionException 確定できない状態のとき
     */
    public void confirm(ClaimCode issued) {
        if (!isRouted()) {
            throw new IllegalStateException(
                    "経路が割り当てられていない予約は確定できません: " + bookingId.value());
        }
        if (issued == null) {
            throw new IllegalArgumentException("引取確認コードは必須です");
        }
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.CONFIRM_BOOKING));
        // **確定と採番はひと組である**（US35）。別の操作にすると、
        // 採番されないまま輸送が始まり、引取の当日に照合する相手が無い
        this.claimCode = issued;
    }

    /** 追跡番号を発行できるか（遷移表 #5。US14）。 */
    public boolean canIssueTrackingNumber() {
        return progress.status().canTransitionBy(BookingCommandType.ASSIGN_TRACKING_NUMBER);
    }

    /**
     * 追跡番号を発行する（US14。遷移表 #5）。
     *
     * @throws InvalidBookingStatusTransitionException 発行できない状態のとき
     */
    public void issueTrackingNumber(BookingTrackingNumber issued) {
        if (issued == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        this.progress = progress.issued(
                progress.status().transitionBy(BookingCommandType.ASSIGN_TRACKING_NUMBER),
                issued);
    }

    /**
     * 輸送を開始できるか（遷移表 #6。US15）。
     *
     * <p><strong>積込は輸送中にも起きる</strong>（積み替え）。そのたびに遷移を
     * 試みると正しい荷役の記録が拒否されるため、荷役の側は本述語で確かめてから
     * 進める。進める必要が無いことは、失敗ではない。
     */
    public boolean canStartTransport() {
        return progress.status().canTransitionBy(BookingCommandType.START_TRANSPORT);
    }

    /**
     * 輸送を開始する（US15。最初の積込による自動遷移。遷移表 #6）。
     *
     * @throws InvalidBookingStatusTransitionException 開始できない状態のとき
     */
    public void startTransport() {
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.START_TRANSPORT));
    }

    /**
     * 配送を完了できるか（遷移表 #7。US16）。
     *
     * <p><strong>引取は 1 度しか成功しない。</strong> 引き渡し済みの貨物に対して
     * 再度引取が登録されても、記録そのものは残しつつ予約状態は動かさない
     * （二重登録は現場で起きうるが、それで状態が壊れてはならない）。
     */
    public boolean canCompleteDelivery() {
        return progress.status().canTransitionBy(BookingCommandType.COMPLETE_DELIVERY);
    }

    /** 引き渡しの完了を取り消せるか（US36。遷移表）。**精算済みは戻せない。** */
    public boolean canRevertDelivery() {
        return progress.status().canTransitionBy(BookingCommandType.REVERT_DELIVERY);
    }

    /**
     * 引き渡しの完了を取り消す（US36）。
     *
     * <p><strong>承認された取り消しだけが呼ぶ。</strong> 手で戻せる形にすると、
     * 引き渡しの証明（US35）が現場の判断で消せることになる。
     *
     * @throws InvalidBookingStatusTransitionException 戻せない状態のとき
     */
    public void revertDelivery() {
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.REVERT_DELIVERY));
    }

    /**
     * 配送を完了する（US16。引取の登録による自動遷移。遷移表 #7）。
     *
     * <p><strong>引き渡し済み以降はキャンセルできない</strong>（{@code BookingStatus}）。
     * 引き渡した貨物の取り消しは返送であり、別の業務である。
     *
     * <p><strong>いつ引取が済んだかを一緒に記録する</strong>（IT13 レビュー C1）。
     * 経理の月次はこの日付で締める。荷役の記録から後で数え直すと、記録が
     * 訂正されたときに請求済みの引取日が動く。
     *
     * @param claimedAt 引取が済んだ日時。<strong>不明なら {@code null}</strong>
     *                  （列が無かったころの経路からも呼べるようにする）
     * @throws InvalidBookingStatusTransitionException 完了できない状態のとき
     */
    public void completeDelivery(java.time.Instant claimedAt) {
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.COMPLETE_DELIVERY));
        this.claimedAt = claimedAt;
    }

    /**
     * 引取が済んだ日時を載せて返す（C1）。
     *
     * <p><strong>復元の引数を増やさない</strong>（{@link #withClaimCode} と同じ形）。
     */
    public Cargo withClaimedAt(java.time.Instant instant) {
        this.claimedAt = instant;
        return this;
    }

    /** 引取が済んだ日時。引取前・旧い行では {@code null}。 */
    public java.time.Instant claimedAt() {
        return claimedAt;
    }

    /**
     * 誤配として記録する（US15。荷役ビジネスルール 1）。
     *
     * <p>積込・荷降しが予定ルートから外れたときに、荷役から ACL 経由で呼ばれる。
     * <strong>予約状態は動かない。</strong> 動くのは経路状態だけであり、
     * 貨物は輸送中のままである（現在地からの再設計は US28 / IT11）。
     *
     * <p>経路が割り当てられていない貨物は誤配にならない。比べる予定が無いためである。
     */
    public void markMisrouted(MisrouteDetection detection) {
        if (!isRouted()) {
            return;
        }
        this.progress = progress.withRouting(
                CargoRouting.misrouted(progress.routing().itinerary()));
        // **運ばれてきた事実を自分の表に写す**（ADR-009 の結果整合）。
        // 予約詳細で現在地を示すために、荷役のテーブルを読みに行かない。
        // 読みに行くと BC をまたぐ SQL になり、**どの検査にも映らない結合**が育つ
        // （IT11 レビュー C28）
        this.misrouteDetection = detection;
    }

    /** 誤配を検知した荷役の写し。誤配でなければ {@code null}。 */
    public MisrouteDetection misrouteDetection() {
        return misrouteDetection;
    }

    /**
     * 予約をキャンセルする。
     *
     * @throws InvalidBookingStatusTransitionException キャンセルできない状態のとき
     */
    public void cancel() {
        this.progress = progress.withStatus(
                progress.status().transitionBy(BookingCommandType.CANCEL_BOOKING));
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public ShipperId shipperId() {
        return shipperId;
    }

    public CargoSpecification cargoSpecification() {
        return cargoSpecification;
    }

    public RouteSpecification routeSpecification() {
        return routeSpecification;
    }

    public BookingStatus bookingStatus() {
        return progress.status();
    }

    /** 予約の進み方（状態・経路・追跡番号）。永続化はこの単位で読み書きする。 */
    public CargoProgress progress() {
        return progress;
    }

    public CargoRoutingStatus routingStatus() {
        return progress.routing().status();
    }

    /** 旅程。割り当て前は {@code null}。 */
    public CargoItinerary cargoItinerary() {
        return progress.routing().itinerary();
    }

    /** 追跡番号。発行前は {@code null}。 */
    public BookingTrackingNumber trackingNumber() {
        return progress.trackingNumber();
    }

    /** 経路が割り当てられているか。 */
    public boolean isRouted() {
        return progress.routing().isRouted();
    }

    public long version() {
        return version;
    }
}
