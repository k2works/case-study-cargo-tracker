package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.port.CargoEventNotifier;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.TrackingNumberIssued;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoRestoration;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 通知・確定・経路設計へ戻す・追跡番号の発行のユースケース（US12〜US14）。
 *
 * <p><strong>ユースケースを足したら、その場で直接のテストを書く</strong>（IT5 の Try 7）。
 * コントローラのモック越しに済ませると、コントローラが呼び方を間違えていないことしか
 * 確かめられない。
 */
@DisplayName("荷主への通知から追跡番号の発行まで")
class NotifyConfirmAndIssueUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant NOW = Instant.parse("2026-08-22T02:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final List<Cargo> saved = new ArrayList<>();
    private final List<TrackingNumberIssued> published = new ArrayList<>();

    /** 採番を何回行ったか。番号を組み立てているのが永続化の側であることを数で確かめる。 */
    private int numberingCalls;

    private Cargo stored = routed(BookingStatus.ROUTE_PROPOSED, null, null);

    private static Cargo routed(BookingStatus bookingStatus,
            com.example.bookingms.domain.model.RouteNotification notification,
            com.example.bookingms.domain.model.TrackingNumber trackingNumber) {
        CargoItinerary itinerary = CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0100"), TOKYO, LOS_ANGELES,
                        Instant.parse("2030-09-02T09:00:00Z"),
                        Instant.parse("2030-09-16T09:00:00Z"))));
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(bookingStatus, TransportStatus.NOT_RECEIVED, RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2030, Month.SEPTEMBER, 1),
                        LocalDate.of(2030, Month.SEPTEMBER, 20)),
                itinerary, notification, trackingNumber);
    }

    /** 旅程がまだ無い予約。経路が決まる前の姿である。 */
    private static Cargo withoutItinerary(BookingStatus bookingStatus,
            com.example.bookingms.domain.model.RouteNotification notification) {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(bookingStatus, TransportStatus.NOT_RECEIVED, RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2030, Month.SEPTEMBER, 1),
                        LocalDate.of(2030, Month.SEPTEMBER, 20)),
                null, notification, null);
    }

    private final CargoRepository cargoes = new CargoRepository() {
        @Override
        public String nextTrackingNumber() {
            numberingCalls++;
            return "TRK-20260822-0001";
        }

        @Override
        public Cargo save(Cargo cargo) {
            saved.add(cargo);
            return cargo;
        }

        @Override
        public Optional<Cargo> findById(Long id) {
            return Optional.of(stored);
        }

        @Override
        public Optional<CargoSummary> findByBookingId(String bookingId) {
            return "BKG-2026000001".equals(bookingId)
                    ? Optional.of(new CargoSummary(stored, "丸紅商事"))
                    : Optional.empty();
        }

        @Override
        public java.util.Optional<CargoSummary> findByTrackingNumber(String trackingNumber) {
            // この検査は追跡番号から引かない。呼ばれたら、テストの前提が変わっている
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<CargoSummary> search(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses,
                com.example.bookingms.domain.model.BookingStatus bookingStatus, int limit) {
            return List.of();
        }

        @Override
        public long count(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses,
                com.example.bookingms.domain.model.BookingStatus bookingStatus) {
            return 0;
        }
    };

    /** **キャンセルのイベントはここでは使わない。**追跡番号の発行だけを見る検査である。 */
    private final CargoEventNotifier events = new CargoEventNotifier() {
        @Override
        public void trackingNumberIssued(
                com.example.bookingms.application.port.TrackingNumberIssued event) {
            published.add(event);
        }

        @Override
        public void cargoCancelled(com.example.bookingms.application.port.CargoCancelled event) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    };

    private final NotifyShipperUseCase notifyShipper = new NotifyShipperUseCase(cargoes, clock);
    private final ConfirmBookingUseCase confirmBooking = new ConfirmBookingUseCase(cargoes);
    private final ReturnToRoutingUseCase returnToRouting = new ReturnToRoutingUseCase(cargoes);
    private final IssueTrackingNumberUseCase issueTrackingNumber =
            new IssueTrackingNumberUseCase(cargoes, events, clock);

    @Test
    @DisplayName("通知すると、いつ・誰が が記録されて保存される")
    void notifiesAndSaves() {
        Cargo notified = notifyShipper.notifyShipper("BKG-2026000001", "sales01").orElseThrow();

        assertThat(notified.bookingStatus()).isEqualTo(BookingStatus.ROUTE_NOTIFIED);
        // 時刻は注入した Clock から取る。テストと実装で同じ時刻源を共有する
        assertThat(notified.routeNotification().orElseThrow().notifiedAt()).isEqualTo(NOW);
        assertThat(notified.routeNotification().orElseThrow().notifiedBy()).isEqualTo("sales01");
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("見つからない予約は空を返す")
    void returnsEmptyForUnknownBooking() {
        assertThat(notifyShipper.notifyShipper("BKG-9999999999", "sales01")).isEmpty();
        assertThat(confirmBooking.confirm("BKG-9999999999")).isEmpty();
        assertThat(returnToRouting.returnToRouting("BKG-9999999999")).isEmpty();
        assertThat(issueTrackingNumber.issue("BKG-9999999999")).isEmpty();
        assertThat(saved).as("見つからないのに保存している").isEmpty();
    }

    @Test
    @DisplayName("通知していない予約の確定は、集約が断る")
    void confirmationIsRefusedByTheAggregate() {
        // 判定をユースケースに書き写さない。書き写すと、入口が増えた数だけ判定が増える
        assertThatThrownBy(() -> confirmBooking.confirm("BKG-2026000001"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("通知した予約を確定できる")
    void confirms() {
        stored = routed(BookingStatus.ROUTE_NOTIFIED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        assertThat(confirmBooking.confirm("BKG-2026000001").orElseThrow().bookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("経路設計へ戻すと、経路の状態も作業待ちに戻る")
    void returnsToRouting() {
        stored = routed(BookingStatus.ROUTE_NOTIFIED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        Cargo returned = returnToRouting.returnToRouting("BKG-2026000001").orElseThrow();

        assertThat(returned.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        assertThat(returned.routingStatus()).isEqualTo(RoutingStatus.ROUTING_REQUESTED);
    }

    /** [ADR-011] と同じ形。集約やユースケースで文字列を作らない。 */
    @Test
    @DisplayName("追跡番号は永続化の経路が採番する")
    void numbersThroughThePersistencePath() {
        stored = routed(BookingStatus.CONFIRMED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        Cargo issued = issueTrackingNumber.issue("BKG-2026000001").orElseThrow();

        assertThat(numberingCalls).as("採番の経路を通っていない").isEqualTo(1);
        assertThat(issued.trackingNumber().orElseThrow().value()).isEqualTo("TRK-20260822-0001");
        assertThat(issued.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
    }

    /**
     * 発行したことを他のサービスへ伝える（[ADR-022] 決定 1・決定 2・決定 7）。
     *
     * <p>ペイロードは<strong>相手が追跡を作るのに要るもの</strong>を載せる。ID だけだと
     * trackingms が同期で問い合わせることになり、非同期にした意味が消える。
     */
    @Test
    @DisplayName("発行したことを、相手が追跡を作れる中身で伝える")
    void publishesWhatTheConsumerNeeds() {
        stored = routed(BookingStatus.CONFIRMED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        issueTrackingNumber.issue("BKG-2026000001");

        assertThat(published).hasSize(1);
        TrackingNumberIssued event = published.get(0);
        // 採番済みで渡す。空で送って相手に採番させない（ADR-022 決定 7）
        assertThat(event.trackingNumber()).isEqualTo("TRK-20260822-0001");
        assertThat(event.bookingId()).isEqualTo("BKG-2026000001");
        assertThat(event.originUnLocode()).isEqualTo("JPTYO");
        assertThat(event.destinationUnLocode()).isEqualTo("USLAX");
        assertThat(event.arrivalDeadline()).isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    /**
     * 到着の見込みも同じイベントで伝える（US18-2・[ADR-024] 決定 4）。
     *
     * <p><strong>別のイベントで送らない。</strong>2 つのイベントは別々のキューを通るため
     * <strong>順序が保証されない</strong>——追跡の作成より先に届いた到着日は、引く相手が
     * 無く捨てられる。<strong>kind の統合環境で実際に起きた</strong>。
     */
    @Test
    @DisplayName("到着の見込みを、追跡番号と同じイベントで伝える")
    void publishesTheEstimatedArrival() {
        stored = routed(BookingStatus.CONFIRMED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        issueTrackingNumber.issue("BKG-2026000001");

        TrackingNumberIssued event = published.get(0);
        // **旅程の最後の荷降し時刻を、業務の暦で日付に切る**（[ADR-010]）。
        // UTC で切ると、時差の分だけ 1 日ずれる
        assertThat(event.estimatedArrival())
                .as("到着の見込みが旅程から導かれていない")
                .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 16));
        // **到着期限とは別物である。**期限は「いつまでに」、見込みは「いつ届くか」
        assertThat(event.estimatedArrival())
                .as("到着期限を到着の見込みとして送っている")
                .isNotEqualTo(event.arrivalDeadline());
    }

    /**
     * <strong>旅程が無ければ空で送る。</strong>
     *
     * <p>受け手は空を「未定」として出す——0 や現在時刻で埋めると、荷主は「今日着く」と
     * 読む（US18-2）。
     */
    @Test
    @DisplayName("旅程が無ければ、到着の見込みは空で送る")
    void sendsAnEmptyEstimatedArrivalWithoutAnItinerary() {
        stored = withoutItinerary(BookingStatus.CONFIRMED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"));

        issueTrackingNumber.issue("BKG-2026000001");

        assertThat(published).hasSize(1);
        assertThat(published.get(0).estimatedArrival())
                .as("旅程が無いのに日付で埋めている")
                .isNull();
    }

    /**
     * <strong>断ったのに伝えない。</strong>
     *
     * <p>「出ること」だけを確かめると、いつでも出す実装でも緑になる。確定していない予約で
     * 発行が断られたとき、イベントが飛ぶと存在しない追跡番号の追跡ができる。
     */
    @Test
    @DisplayName("発行を断ったときは何も伝えない")
    void publishesNothingWhenIssuingIsRefused() {
        stored = routed(BookingStatus.ROUTE_NOTIFIED,
                com.example.bookingms.domain.model.RouteNotification.of(NOW, "sales01"), null);

        assertThatThrownBy(() -> issueTrackingNumber.issue("BKG-2026000001"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(published).as("断ったのにイベントを流している").isEmpty();
        assertThat(saved).isEmpty();
    }
}
