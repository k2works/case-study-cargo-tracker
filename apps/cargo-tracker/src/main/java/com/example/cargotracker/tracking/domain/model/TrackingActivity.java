package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.LocalDate;
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
    private final List<TrackingActivityEvent> events;
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
    private Location destination;

    private LocalDate estimatedArrival;

    private TrackingActivity(
            TrackingNumber trackingNumber,
            TrackingBookingId bookingId,
            TransportStatus transportStatus,
            List<TrackingActivityEvent> events,
            long version,
            Location destination,
            LocalDate estimatedArrival) {
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.transportStatus = transportStatus;
        this.events = new ArrayList<>(events);
        this.version = version;
        this.destination = destination;
        this.estimatedArrival = estimatedArrival;
    }

    /**
     * 追跡番号を発行して追跡を始める（US14。遷移表 #5 に対応）。
     *
     * <p>発行直後は「未受取」である。受入基準の「受領待ち」はこの状態を指す。
     */
    public static TrackingActivity issue(
            TrackingNumber trackingNumber, TrackingBookingId booking,
            Location destination, LocalDate estimatedArrival) {
        if (trackingNumber == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (booking == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        return new TrackingActivity(
                trackingNumber, booking, TransportStatus.initial(), List.of(), 0L,
                destination, estimatedArrival);
    }

    /** 永続化された状態から復元する。 */
    public static TrackingActivity reconstruct(
            TrackingNumber trackingNumber,
            TrackingBookingId bookingId,
            TransportStatus transportStatus,
            List<TrackingActivityEvent> events,
            long version,
            Location destination,
            LocalDate estimatedArrival) {
        if (transportStatus == null) {
            throw new IllegalArgumentException("輸送状態は必須です");
        }
        return new TrackingActivity(
                trackingNumber, bookingId, transportStatus, events, version,
                destination, estimatedArrival);
    }

    /**
     * 経路が変わったことを反映する（{@code CargoRoutedEvent} の購読。ADR-012）。
     *
     * <p><strong>発行時の受け渡しだけでは足りない。</strong> 経路を変えても
     * 古い到着予定が残り続ける。片方だけ入れると、消したはずの問題が
     * 「表示が古い」という別の形で残る。
     */
    public void reroute(Location newDestination, LocalDate newEstimatedArrival) {
        this.destination = newDestination;
        this.estimatedArrival = newEstimatedArrival;
    }

    /** 目的地。発行時に予約から渡される。 */
    public Location destination() {
        return destination;
    }

    /** 推定到着日。経路が未確定なら {@code null}。 */
    public LocalDate estimatedArrival() {
        return estimatedArrival;
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
        event.type().resultingStatus().ifPresent(next -> this.transportStatus = next);
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
