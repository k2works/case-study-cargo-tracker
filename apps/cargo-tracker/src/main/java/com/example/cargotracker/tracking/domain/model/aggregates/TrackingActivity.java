package com.example.cargotracker.tracking.domain.model.aggregates;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.entities.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionOccurrence;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionResolution;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingDestination;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 追跡レコード。Tracking Context の集約ルート（US14 / US15 / US18）。
 *
 * <p><strong>輸送状態はイベントから導出せず、保存した値を使う。</strong> 履歴から
 * 毎回計算し直すと、ユニットテストが緑でもリクエストをまたいだときに状態が
 * 巻き戻る（例外の解決で戻る先を履歴から導けないのと同じ理由）。
 *
 * <p><strong>Setter を持たない。</strong> 状態を変える手段は
 * {@link #recordEvent(TrackingActivityEvent)} に限る。
 */
public class TrackingActivity {

    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;

    /**
     * 引取の直前の輸送状態（US36）。
     *
     * <p><strong>取り消しの復帰先を履歴から導き直さない。</strong>
     * 例外の {@code statusBefore} と同じ判断である。
     */
    private TransportStatus statusBeforeClaim;
    private final List<TrackingActivityEvent> events;

    /**
     * 例外の記録（US19 / US20）。
     *
     * <p><strong>集約の内側に置く。</strong> 例外は輸送状態を動かし、解決で元に戻す。
     * 別の集約にすると、状態を変える権限が 2 か所に分かれ、
     * 「例外を解決したのに状態が戻っていない」形を作れてしまう。
     */
    private final List<TrackingExceptionEvent> exceptions;

    private final long version;

    private TransportStatus transportStatus;

    /**
     * 目的地と推定到着日（ADR-012）。
     *
     * <p><strong>Booking から問い合わせず、自分で持つ。</strong> 問い合わせると
     * Tracking → Booking の参照が生まれ、追跡番号の発行（Booking → Tracking）と
     * 合わせてパッケージが循環する。
     *
     * <p><strong>結果整合の写しである。</strong> 予約が経路を変えたときは
     * {@code CargoRoutedEvent} の購読で追随する。反映には間がある（ADR-009 の代償）。
     */
    private TrackingDestination destination;

    private TrackingActivity(
            TrackingNumber trackingNumber,
            TrackingBookingId bookingId,
            TransportStatus transportStatus,
            List<TrackingActivityEvent> events,
            List<TrackingExceptionEvent> exceptions,
            long version,
            TrackingDestination destination) {
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.transportStatus = transportStatus;
        this.events = new ArrayList<>(events);
        this.exceptions = new ArrayList<>(exceptions);
        this.version = version;
        this.destination = destination == null ? TrackingDestination.unknown() : destination;
    }

    /**
     * 追跡番号を発行して追跡を始める（US14。遷移表 #5 に対応）。
     *
     * <p>発行直後は「未受取」である。受入基準の「受領待ち」はこの状態を指す。
     */
    public static TrackingActivity issue(
            TrackingNumber trackingNumber, TrackingBookingId booking,
            TrackingDestination destination) {
        if (trackingNumber == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (booking == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        return new TrackingActivity(
                trackingNumber, booking, TransportStatus.initial(), List.of(), List.of(), 0L,
                destination);
    }

    /** 永続化された状態から復元する。 */
    public static TrackingActivity reconstruct(
            TrackingNumber trackingNumber,
            TrackingBookingId bookingId,
            TransportStatus transportStatus,
            List<TrackingActivityEvent> events,
            List<TrackingExceptionEvent> exceptions,
            long version,
            TrackingDestination destination) {
        if (transportStatus == null) {
            throw new IllegalArgumentException("輸送状態は必須です");
        }
        return new TrackingActivity(
                trackingNumber, bookingId, transportStatus, events, exceptions, version,
                destination);
    }

    /**
     * 引取の直前の状態を載せて返す（US36）。
     *
     * <p><strong>復元の引数を増やさない</strong>（{@code Cargo.withClaimCode} と同じ形）。
     * <strong>値が無くても復元は成り立つ</strong> — 列が無かったころに
     * 引き取られた貨物は持たない。
     */
    public TrackingActivity withStatusBeforeClaim(TransportStatus before) {
        this.statusBeforeClaim = before;
        return this;
    }

    /**
     * 経路が変わったことを反映する（{@code CargoRoutedEvent} の購読。ADR-012）。
     *
     * <p><strong>発行時の受け渡しだけでは足りない。</strong> 経路を変えても
     * 古い到着予定が残り続ける。片方だけ入れると、消したはずの問題が
     * 「表示が古い」という別の形で残る。
     */
    public void reroute(TrackingDestination newDestination) {
        this.destination = newDestination == null ? TrackingDestination.unknown() : newDestination;
    }

    /** 行き先（目的地と推定到着日）。発行時に予約から渡される。 */
    public TrackingDestination destination() {
        return destination;
    }

    /**
     * 荷役の結果を記録する（US15）。
     *
     * <p>輸送状態はイベント種別が決める（{@link TrackingEventType#resultingStatus()}）。
     * <strong>状態を動かさない種別（通関）でも、イベントは残す。</strong> 起きた事実を
     * 記録しないと、あとから「いつ通関したのか」を追えない。
     *
     * <p><strong>過去に遡る記録も受け付ける。</strong> 現場は後から入力することがあり、
     * 拒否すると記録そのものが残らない。ただし一覧は発生日時の順で返す。
     */
    public void recordEvent(TrackingActivityEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("追跡イベントは必須です");
        }
        events.add(event);
        event.type().resultingStatus().ifPresent(next -> {
            // **引取の直前の状態を残してから進む**（US36）。取り消しの復帰先を
            // 履歴から導き直すと、ユニットテストが緑でもリクエストをまたいだときに
            // 誤った状態に復帰する（例外の statusBefore と同じ判断）
            if (next == TransportStatus.CLAIMED) {
                this.statusBeforeClaim = transportStatus;
            }
            this.transportStatus = next;
        });
    }

    /**
     * 引取の取り消しを反映する（US36）。
     *
     * <p><strong>承認された取り消しだけが呼ぶ。</strong> 追跡管理者の承認なしに
     * 状態は戻らない。手動更新（US17）が逆行を拒むのはこのためである。
     *
     * <p><strong>戻る先は記録しておいた値である。</strong> 記録が無ければ
     * （列が無かったころに引き取られた貨物）荷降し済みに戻す —
     * <strong>引取の直前は荷降しか引取待ちであり、そのどちらでも
     * 「まだ渡していない」ことは表せる</strong>。
     */
    public void cancelClaim() {
        if (transportStatus != TransportStatus.CLAIMED) {
            throw new IllegalStateException(
                    "引取が完了していない貨物の引取は取り消せません。現在は「%s」です"
                            .formatted(transportStatus.displayName()));
        }
        this.transportStatus = statusBeforeClaim == null
                ? TransportStatus.UNLOADED : statusBeforeClaim;
        this.statusBeforeClaim = null;
    }

    /** 引取の直前の輸送状態（US36）。引取前・列が無かったころは {@code null}。 */
    public TransportStatus statusBeforeClaim() {
        return statusBeforeClaim;
    }

    /**
     * 状態を手で更新する（US17）。
     *
     * <p><strong>逆行は拒否する。</strong> 進んだ状態より前へ戻す更新は受け付けない。
     * 戻す必要が生じるのは誤登録の訂正であり、それは承認を伴う取り消し（US36）で扱う。
     * 手動更新で黙って戻せると、<strong>引き渡し済みの貨物を輸送中に戻せてしまう。</strong>
     *
     * <p><strong>拒否したときはイベントも残さない。</strong> 起きなかった出来事を
     * 履歴に記録すると、あとから読む人はそれが起きたと信じる。
     *
     * <p><strong>時系列も守る（C4 / C10）。</strong> 状態を動かさない種別（入港）は
     * 逆行の検査を素通りするため、状態の比較だけでは
     * <strong>引取完了の後に入港を入れられる</strong>。履歴に「引き取ったあとに
     * 船が着いた」という消せない矛盾が残る。
     *
     * @param now 業務上の現在時刻。**未来の出来事は履歴に書かせない**
     * @throws IllegalStateException 逆行する更新・時系列に反する更新のとき
     */
    public void updateManually(TrackingActivityEvent event, java.time.Instant now) {
        if (event == null) {
            throw new IllegalArgumentException("追跡イベントは必須です");
        }
        if (!event.manual()) {
            throw new IllegalArgumentException("手動更新のイベントではありません");
        }
        // **輸送が終わった貨物に新しい出来事は起きない。**
        // 状態を動かさない種別でも、ここで止めなければ矛盾した履歴を作れる
        if (transportStatus == TransportStatus.CLAIMED) {
            throw new IllegalStateException(
                    "引取が完了した貨物は手動で更新できません。"
                            + "誤って登録した場合はシステム管理担当窓口へご連絡ください");
        }
        if (now != null && event.occurredAt().isAfter(now)) {
            throw new IllegalStateException(
                    "発生日時に未来の日時は指定できません。まだ起きていない出来事は記録できません");
        }
        TrackingActivityEvent latest = latestEvent();
        if (latest != null && event.occurredAt().isBefore(latest.occurredAt())) {
            throw new IllegalStateException(
                    "発生日時が直前の記録（%s の %s）より前です。"
                            .formatted(latest.type().displayName(), latest.occurredAt())
                            + "時系列が前後した履歴は、どちらが後なのかを判断できません");
        }
        event.type().resultingStatus().ifPresent(next -> {
            if (!transportStatus.canAdvanceTo(next)) {
                throw new IllegalStateException(
                        "現在の状態（%s）より前の状態（%s）には戻せません。"
                                .formatted(transportStatus.displayName(), next.displayName())
                                + "誤って登録した場合はシステム管理担当窓口へご連絡ください");
            }
        });
        recordEvent(event);
    }

    /**
     * 例外を起票する（US19 / US20）。
     *
     * <p><strong>発生前の輸送状態を記録してから</strong>状態を「例外」に変える。
     * 解決時の復帰先を履歴から導き直さないためである。
     *
     * <p><strong>引取が完了した貨物には起票できない。</strong> 輸送が終わった貨物に
     * 遅延も紛失も起きない。手動更新を引取後に塞いだのと同じ判断であり、
     * 塞がないと<strong>解決したときに「引取完了」へ戻す</strong>という
     * 意味の通らない操作ができてしまう。
     *
     * <p><strong>未解決の例外は同時に何件でもよい</strong>（IT11 / C21）。
     * 遅延の対応中に破損が判明することは実務では珍しくない。1 件に限ると、
     * 破損を登録するために遅延を「解決」する必要が生まれ、その瞬間に荷主へ
     * <strong>事実でない対応報告</strong>が飛ぶ。
     *
     * <p>復帰先は<strong>最初に起票された未解決の例外の発生前の状態に固定する</strong>。
     * 2 件目以降が「例外発生」を発生前の状態として持つことはない
     * （持つと、解決しても例外のままという行き止まりができる）。
     *
     * @param now 業務上の現在時刻。**未来に起きた例外は記録できない**
     * @return 起票した例外
     */
    public TrackingExceptionEvent raiseException(
            ExceptionOccurrence occurrence, java.time.Instant now) {
        if (transportStatus == TransportStatus.CLAIMED) {
            throw new IllegalStateException(
                    "引取が完了した貨物には例外を登録できません。"
                            + "輸送が終わった貨物に遅延・破損・紛失は起きません");
        }
        if (now != null && occurrence != null && occurrence.occurredAt().isAfter(now)) {
            throw new IllegalStateException(
                    "発生日時に未来の日時は指定できません。まだ起きていない出来事は記録できません");
        }
        TrackingExceptionEvent raised =
                TrackingExceptionEvent.raise(occurrence, restoreTarget());
        exceptions.add(raised);
        this.transportStatus = TransportStatus.EXCEPTION;
        return raised;
    }

    /**
     * 例外の対応が済んだことを記録し、<strong>発生前の状態へ戻す</strong>（US19 / US20）。
     *
     * <p>戻す先は例外自身が持つ {@code statusBefore} である。
     * <strong>履歴から導き直さない</strong> — 例外の発生中に荷役が記録されていると、
     * 導出は「例外の直前」ではなく「最後の荷役」を指してしまう。
     *
     * <p><strong>まだ未解決の例外が残っているうちは戻さない</strong>（C21）。
     * 破損が片づいても遅延が続いているなら、貨物はまだ例外の中にある。
     * ここで通常状態に戻すと、例外一覧から消えて誰も見なくなる。
     *
     * <p><strong>新しい到着予定日は追跡の行き先にも反映する</strong>（US19 / C18）。
     * 例外に書き残すだけでは、荷主が見る追跡照会の到着予定は古いままである。
     * 「対応内容を入力できた」ことと「荷主に正しく伝わった」ことは違う。
     *
     * @return 解決した例外
     */
    public TrackingExceptionEvent resolveException(
            long exceptionId, ExceptionResolution resolution, java.time.Instant now) {
        TrackingExceptionEvent target = exceptions.stream()
                .filter(e -> e.id() == exceptionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("該当する例外がありません"));
        target.resolve(resolution, now);
        if (!hasActiveException()) {
            this.transportStatus = target.statusBefore();
        }
        if (resolution.hasRevisedArrival()) {
            // 目的地は変えない。**変わったのは「いつ着くか」だけである**
            this.destination = new TrackingDestination(
                    destination.location(), resolution.revisedArrival());
        }
        return target;
    }

    /**
     * 例外がすべて片づいたときに戻る先。
     *
     * <p><strong>最初に起票された未解決の例外の発生前の状態に固定する。</strong>
     * 未解決が無ければ、いまの状態がそのまま戻る先である
     * （＝これから起票する 1 件目の {@code statusBefore}）。
     */
    private TransportStatus restoreTarget() {
        return exceptions.stream()
                .filter(e -> !e.isResolved())
                .map(TrackingExceptionEvent::statusBefore)
                .findFirst()
                .orElse(transportStatus);
    }

    /** 未解決の例外を抱えているか。 */
    public boolean hasActiveException() {
        return exceptions.stream().anyMatch(e -> !e.isResolved());
    }

    /** 例外を発生の新しい順に返す。**直近に何が起きたかが先に読めるようにする。** */
    public List<TrackingExceptionEvent> exceptions() {
        return exceptions.stream()
                .sorted(Comparator.comparing(TrackingExceptionEvent::occurredAt).reversed())
                .toList();
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public TrackingBookingId bookingId() {
        return bookingId;
    }

    public TransportStatus transportStatus() {
        return transportStatus;
    }

    /** イベントを発生日時の古い順で返す。 */
    public List<TrackingActivityEvent> events() {
        return events.stream()
                .sorted(Comparator.comparing(TrackingActivityEvent::occurredAt))
                .toList();
    }

    /** 最新のイベント。1 件も無ければ {@code null}。 */
    public TrackingActivityEvent latestEvent() {
        List<TrackingActivityEvent> ordered = events();
        return ordered.isEmpty() ? null : ordered.getLast();
    }

    public long version() {
        return version;
    }
}
