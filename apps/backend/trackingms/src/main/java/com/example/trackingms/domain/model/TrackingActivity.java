package com.example.trackingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 貨物の追跡（集約ルート）。
 *
 * <p>IT7 で進むのは<strong>荷役に応じた状態の遷移まで</strong>である。例外の起票（US20）と
 * 出港の反映（US17）は IT8 で足す。<strong>縮小実装であることを明記する</strong>
 * ——書かないと実装漏れと読まれる。
 *
 * <p>追跡番号は<strong>受け取る</strong>（[ADR-022] 決定 7）。ここでは採番しない。
 */
public final class TrackingActivity {

    private final Long id;
    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private final TrackingStatus trackingStatus;

    /**
     * 例外が起きる前の状態（[ADR-024] 決定 2）。
     *
     * <p><strong>列に持つ。履歴から導かない。</strong>再導出は、1 リクエストの中では
     * 履歴が手元にあるので正しく見え、<strong>行に残っていないことに気づけない</strong>。
     * ユニットが緑のままクロスリクエストで誤復帰する。
     */
    private final TrackingStatus statusBefore;

    private final Location origin;
    private final Location destination;

    /** いま貨物がある港。まだ動いていなければ出発港。 */
    private final Location currentLocation;

    private final LocalDate arrivalDeadline;

    /**
     * 推定到着日（US18-2）。
     *
     * <p><strong>到着期限とは別物である。</strong>期限は「いつまでに届けるか」、
     * こちらは「いつ届く見込みか」。経路が決まるまでは分からない——
     * <strong>0 や今日で埋めない</strong>。分からないことは分からないと出す。
     */
    private final LocalDate estimatedArrival;

    /** 起票された例外。未解決は 1 件までである（決定 2）。 */
    private final TrackingExceptionEvent activeException;

    // S107（引数が多い）: 復元は永続化された行の写しであり、列数がそのまま現れる
    @SuppressWarnings("java:S107")
    private TrackingActivity(Long id, TrackingNumber trackingNumber, TrackingBookingId bookingId,
            TrackingStatus trackingStatus, TrackingStatus statusBefore, Location origin,
            Location destination, Location currentLocation, LocalDate arrivalDeadline,
            LocalDate estimatedArrival, TrackingExceptionEvent activeException) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.trackingStatus = trackingStatus;
        this.statusBefore = statusBefore;
        this.origin = origin;
        this.destination = destination;
        this.currentLocation = currentLocation;
        this.arrivalDeadline = arrivalDeadline;
        this.estimatedArrival = estimatedArrival;
        this.activeException = activeException;
    }

    /** 項目を 1 つだけ差し替えた写しを作る。組み立てを 1 か所に置く。 */
    @SuppressWarnings("java:S107")
    private TrackingActivity with(TrackingStatus nextStatus, TrackingStatus nextStatusBefore,
            Location nextLocation, LocalDate nextEstimatedArrival,
            TrackingExceptionEvent nextException) {
        return new TrackingActivity(id, trackingNumber, bookingId, nextStatus, nextStatusBefore,
                origin, destination, nextLocation, arrivalDeadline, nextEstimatedArrival,
                nextException);
    }

    /**
     * 追跡を始める。ここでだけ入力を検査する。
     *
     * <p>状態は空欄にせず、意味のある初期値を置く（[ADR-009]）。貨物はまだ動いていない。
     */
    public static TrackingActivity start(TrackingNumber trackingNumber,
            TrackingBookingId bookingId, Location origin, Location destination,
            LocalDate arrivalDeadline) {
        if (trackingNumber == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (bookingId == null) {
            throw new IllegalArgumentException("予約番号は必須です");
        }
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
        return new TrackingActivity(null, trackingNumber, bookingId,
                TrackingStatus.NOT_RECEIVED, null, origin, destination, origin, arrivalDeadline,
                null, null);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない。
     *
     * <p>列が無かったころの行が読めなくなるため、新しい列は空を許す
     * （現在地は空なら出発港、[ADR-009] の考え方）。
     */
    @SuppressWarnings("java:S107")
    public static TrackingActivity restore(Long id, TrackingNumber trackingNumber,
            TrackingBookingId bookingId, TrackingStatus trackingStatus,
            TrackingStatus statusBefore, Location origin, Location destination,
            Location currentLocation, LocalDate arrivalDeadline, LocalDate estimatedArrival,
            TrackingExceptionEvent activeException) {
        return new TrackingActivity(id, trackingNumber, bookingId, trackingStatus, statusBefore,
                origin, destination, currentLocation == null ? origin : currentLocation,
                arrivalDeadline, estimatedArrival, activeException);
    }

    /**
     * 荷役の記録に応じて状態を進める（US15-4・[ADR-023] 決定 5）。
     *
     * <p><strong>戻せる遷移は作らない。</strong>荷役は実際に起きた作業であり、記録が届いた
     * 順に進む。届く順が入れ替わることはあるが、そのときに「戻す」と、あとから届いた古い
     * 作業で追跡が巻き戻る。
     *
     * <p>進む先を決めるのも、戻る向きかを決めるのも {@link TrackingStatus} である。
     * ここで種別や状態を見比べると、判定が集約と列挙の 2 か所に分かれる。
     *
     * <p><strong>進まないときは、同じものをそのまま返す。</strong>呼び出し側はこれを見て
     * 「書き込まない」を判断する。新しい実体を返すと、同じ内容の更新で行を触り続ける。
     *
     * @param handlingType 荷役の種別の名前（相手の型は持ち込まない）
     * @param locationUnLocode 作業場所
     * @return 進めた追跡。進まないときは、そのままの自分
     */
    public TrackingActivity afterHandling(String handlingType, String locationUnLocode) {
        boolean atDestination = destination.unLocode().equals(locationUnLocode);
        return TrackingStatus.afterHandling(handlingType, atDestination)
                .filter(trackingStatus::canAdvanceTo)
                .map(next -> with(next, statusBefore,
                        Location.of(locationUnLocode, currentLocationNameFor(locationUnLocode)),
                        estimatedArrival, activeException))
                .orElse(this);
    }

    /**
     * 港の名前は分からないことがある。
     *
     * <p>荷役のイベントが運ぶのは UN/LOCODE だけで、名前はこちらのマスタから引く。
     * 引くのは集約の仕事ではないため、<strong>コードをそのまま名前として置く</strong>
     * ——呼び出し側がマスタから引き直したものを渡すときは、そちらが正になる。
     */
    private String currentLocationNameFor(String unLocode) {
        if (origin.unLocode().equals(unLocode)) {
            return origin.name();
        }
        if (destination.unLocode().equals(unLocode)) {
            return destination.name();
        }
        return unLocode;
    }

    /**
     * 状態を手で更新する（US17-2・[ADR-024] 決定 1）。
     *
     * <p><strong>手動だから自由に動かせる、とはしない。</strong>荷主が見ているのは 1 本の
     * 状態であり、どの入口から動いたかは荷主に見えない。手動経路にだけ抜け道を作ると、
     * IT7 で塞いだ巻き戻りが人の操作で起きる。
     *
     * <p>判定は {@link TrackingStatus#canAdvanceTo} をそのまま使う。<strong>手動用に
     * 書き直さない</strong>——書き直すと、規則が 2 か所に分かれる。
     *
     * @throws IllegalStateException 例外が起きているとき
     * @throws IllegalArgumentException 進む向きでないとき
     */
    public TrackingActivity updateManually(TrackingStatus next, Location location,
            Instant occurredAt) {
        requireNoActiveException("状態を手で更新できません");
        if (next == null || location == null || occurredAt == null) {
            throw new IllegalArgumentException("状態・現在地・日時は必須です");
        }
        if (!trackingStatus.canAdvanceTo(next)) {
            throw new IllegalArgumentException(
                    "前の状態には戻せません。誤りを直すには、例外として起票してください");
        }
        return with(next, statusBefore, location, estimatedArrival, activeException);
    }

    /**
     * 例外を起票する（US19-1・US20-1・[ADR-024] 決定 2）。
     *
     * <p><strong>発生前の状態を持つ。</strong>解決したときに戻る先である。履歴から
     * 導くと、行に残っていないことに気づけない。
     *
     * <p><strong>未解決の例外は 1 件までである。</strong>2 件目を許すと、発生前の状態が
     * {@code EXCEPTION} で上書きされ、解決しても戻れなくなる。
     */
    public TrackingActivity raiseException(ExceptionType exceptionType, String description,
            Instant occurredAt) {
        requireNoActiveException("例外を起票できません");
        if (exceptionType != null && !exceptionType.raisableByOperator()) {
            throw new IllegalArgumentException(
                    "%s は自動で検知されるため、手では起票できません".formatted(exceptionType.label()));
        }
        return recordException(exceptionType, description, occurredAt);
    }

    /**
     * 仕組みが検知した例外を起票する（US28 の誤配・US29 の税関保留）。
     *
     * <p><strong>人の手番とは入口を分ける</strong>（IT9 返済枠 0.4）。同じ入口に
     * 「これは仕組みからだ」という引数を足すと、呼び出し側のどれが人でどれが仕組みかが
     * コードから読めなくなり、手番の検査は入口が増えるたびに写される。入口を分ければ、
     * それぞれが自分の断り方だけを持つ。
     *
     * <p><strong>人が決める種別は、ここからは入れない。</strong>遅延・破損・紛失は
     * 人が見て決めることである。購読側の不具合で「遅延」が誰の判断も経ずに立つと、
     * 追跡管理者は自分が起票していない例外の説明を求められる。
     */
    public TrackingActivity detectException(ExceptionType exceptionType, String description,
            Instant occurredAt) {
        requireNoActiveException("例外を起票できません");
        if (exceptionType != null && exceptionType.raisableByOperator()) {
            throw new IllegalArgumentException(
                    "%s は担当者が判断して起票するため、自動では起票できません"
                            .formatted(exceptionType.label()));
        }
        return recordException(exceptionType, description, occurredAt);
    }

    /** 起票の中身。手番の判断は呼び出し元（{@code raiseException} / {@code detectException}）が持つ。 */
    private TrackingActivity recordException(ExceptionType exceptionType, String description,
            Instant occurredAt) {
        TrackingExceptionEvent raised =
                TrackingExceptionEvent.raise(exceptionType, description, occurredAt);
        return with(TrackingStatus.EXCEPTION, trackingStatus, currentLocation, estimatedArrival,
                raised);
    }

    /**
     * 例外を解決する（US19-4）。
     *
     * <p><strong>発生前の状態に戻る。</strong>初期状態へ戻すのではない——受領待ちまで
     * 巻き戻ると、荷主は「届いていたはずの貨物が出発前に戻った」を見る。
     *
     * <p><strong>遅延を解決するなら、いつ着くのかを言う</strong>（IT9 返済枠 0.6）。
     * 遅延の解決とは「遅れの見通しが立った」ことである。到着予定日を入れずに閉じると、
     * 遅れる前の古い予定日が残り続け、荷主は過ぎた日付を見る。それは「解決した」の
     * 意味ではない。破損・紛失には求めない——到着の見込みとは別の話であり、求めると
     * 担当者は形だけの日付を入れて閉じる。
     *
     * @param newEstimatedArrival 新しい到着予定日。遅延では必須、それ以外は空なら据え置く
     */
    public TrackingActivity resolveException(Long exceptionId, String resolutionNotes,
            Instant resolvedAt, LocalDate newEstimatedArrival) {
        if (activeException == null || !activeException.unresolved()) {
            throw new IllegalStateException("未解決の例外がありません");
        }
        // **開いていた一覧が古いまま閉じられない**（IT9 返済枠 0.7）。
        // 一覧を開いたまま席を外している間に、別の担当者が同じ例外を解決し、
        // 次の例外が起票されることがある。照合しないと、見ていたのとは違う例外を、
        // 見ていたつもりの理由で閉じる
        if (exceptionId != null && !exceptionId.equals(activeException.id())) {
            throw new IllegalStateException(
                    "別の担当者がこの例外をすでに解決しています。一覧を開き直してください");
        }
        if (activeException.exceptionType() == ExceptionType.DELAY && newEstimatedArrival == null) {
            throw new IllegalArgumentException(
                    "遅延を解決するときは、新しい到着予定日を入れてください");
        }
        if (statusBefore == null) {
            // **戻る先が分からない行は解決できない。**復元は列の空を許す（列が無かった
            // ころの行が読めなくなるため）ので、ここに来る行がありうる。そのまま進めると
            // 状態が空になり、ずっと先で読めない行として現れる
            throw new IllegalStateException(
                    "この例外は発生前の状態を持っていません。運用へ連絡してください");
        }
        // **解決しても消さない。**実際に起きたことの記録である（[ADR-023] 決定 3 と同じ立場）。
        // 保存先はこれを読んで、解決したことを行へ足す
        TrackingExceptionEvent resolved = activeException.resolve(resolutionNotes, resolvedAt);
        return with(statusBefore, null, currentLocation,
                newEstimatedArrival == null ? estimatedArrival : newEstimatedArrival, resolved);
    }

    /**
     * 推定到着日を受け取る（US18-2・[ADR-024] 決定 4）。
     *
     * <p>bookingms の {@code TrackingNumberIssued} が運ぶ（[ADR-024] 決定 4）。
     * 経路が決まるまでは分からない。
     */
    public TrackingActivity withEstimatedArrival(LocalDate newEstimatedArrival) {
        if (newEstimatedArrival == null || newEstimatedArrival.equals(estimatedArrival)) {
            // 進まないときは同じものを返す。呼び出し側はこれを見て「書き込まない」を判断する
            return this;
        }
        return with(trackingStatus, statusBefore, currentLocation, newEstimatedArrival,
                activeException);
    }

    private void requireNoActiveException(String what) {
        if (activeException != null && activeException.unresolved()) {
            throw new IllegalStateException("この貨物には未解決の例外があります。" + what);
        }
    }

    /** 未解決の例外。解決したものは含まない。 */
    public Optional<TrackingExceptionEvent> activeException() {
        return Optional.ofNullable(activeException).filter(TrackingExceptionEvent::unresolved);
    }

    /**
     * 直前に扱った例外。解決したものも含む。
     *
     * <p>保存先が「解決したことを行へ足す」ために読む。{@link #activeException()} は
     * 未解決だけを返すので、解決の直後は空になる。
     */
    public Optional<TrackingExceptionEvent> lastException() {
        return Optional.ofNullable(activeException);
    }

    /** 緊急の例外が起きているか（[ADR-024] 決定 3）。判定は種別が持つ。 */
    public boolean hasUrgentException() {
        return activeException().map(TrackingExceptionEvent::urgent).orElse(false);
    }

    /** 例外が起きる前の状態。未解決の例外が無ければ空。 */
    public Optional<TrackingStatus> statusBefore() {
        return Optional.ofNullable(statusBefore);
    }

    public Location currentLocation() {
        return currentLocation;
    }

    /** 推定到着日。<strong>分からなければ空</strong>——0 や今日で埋めない。 */
    public Optional<LocalDate> estimatedArrival() {
        return Optional.ofNullable(estimatedArrival);
    }

    public Long id() {
        return id;
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public TrackingBookingId bookingId() {
        return bookingId;
    }

    public TrackingStatus trackingStatus() {
        return trackingStatus;
    }

    public Location origin() {
        return origin;
    }

    public Location destination() {
        return destination;
    }

    public LocalDate arrivalDeadline() {
        return arrivalDeadline;
    }
}
