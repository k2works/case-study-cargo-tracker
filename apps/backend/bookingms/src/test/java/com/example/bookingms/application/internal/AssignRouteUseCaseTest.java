package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateFinder;
import com.example.bookingms.application.port.RouteCandidateQuery;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 経路の割り当て（US09・[ADR-019]・[ADR-020]）。
 *
 * <p>とくに<strong>確定時の成立の再検証</strong>（ADR-019 決定 2）をここで固定する。
 */
@DisplayName("経路の割り当て")
class AssignRouteUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");

    /** routingms が返す候補。テストで差し替えて「航海が消えた」状況を作る。 */
    private final List<CargoItinerary> availableCandidates = new ArrayList<>();
    private final List<RouteCandidateQuery> askedWith = new ArrayList<>();
    private final List<Cargo> saved = new ArrayList<>();

    private final RouteCandidateFinder routeCandidates = query -> {
        askedWith.add(query);
        return List.copyOf(availableCandidates);
    };

    /** 目的地のタイムゾーン。マスタから消えた状態を作るために {@code null} にできる。 */
    private ZoneId destinationZone = ZoneId.of("America/Los_Angeles");

    private final LocationRepository locations = new LocationRepository() {
        @Override
        public List<Location> findAll() {
            return List.of(TOKYO, LOS_ANGELES, BUSAN);
        }

        @Override
        public Optional<Location> findByUnLocode(String unLocode) {
            return findAll().stream().filter(l -> l.unLocode().equals(unLocode)).findFirst();
        }

        @Override
        public Optional<ZoneId> timeZoneOf(String unLocode) {
            return Optional.ofNullable(destinationZone);
        }
    };

    private Cargo stored = requested();

    private final CargoRepository cargoes = new CargoRepository() {
        @Override
        public String nextTrackingNumber() {
            throw new UnsupportedOperationException("このテストでは採番しない");
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

    private final AssignRouteUseCase useCase =
            new AssignRouteUseCase(cargoes, locations, routeCandidates);

    private static Cargo requested() {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.PRELIMINARY, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTING_REQUESTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2030, Month.SEPTEMBER, 1),
                        LocalDate.of(2030, Month.SEPTEMBER, 20)));
    }

    private static CargoItinerary direct() {
        return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"), TOKYO, LOS_ANGELES,
                Instant.parse("2030-09-02T09:00:00Z"), Instant.parse("2030-09-15T09:00:00Z"))));
    }

    private static CargoItinerary viaBusan() {
        return CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0201"), TOKYO, BUSAN,
                        Instant.parse("2030-09-02T09:00:00Z"),
                        Instant.parse("2030-09-04T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0202"), BUSAN, LOS_ANGELES,
                        Instant.parse("2030-09-05T09:00:00Z"),
                        Instant.parse("2030-09-16T09:00:00Z"))));
    }

    @Test
    @DisplayName("いま算出できる候補の中にあれば割り当てる")
    void assignsAvailableItinerary() {
        availableCandidates.add(direct());

        Cargo assigned = useCase.assign("BKG-2026000001", direct(), null).orElseThrow().cargo();

        assertThat(assigned.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        assertThat(assigned.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        assertThat(saved).hasSize(1);
    }

    /**
     * ADR-019 決定 2。確かめずに通すと欠航した航海の旅程が予約に入る。
     *
     * <p>荷役の担当者は来ない船を待ち、荷主には出ない便の予定が伝わる。しかも間違いに
     * 気づくのは出港予定日である。
     */
    @Test
    @DisplayName("航海が変わって候補から消えていれば、確定を断る")
    void rejectsItineraryThatIsNoLongerAvailable() {
        // 候補を出したあとに V0100 が欠航し、いまは釜山経由しか出ない
        availableCandidates.add(viaBusan());

        CargoItinerary chosen = direct();

        assertThatThrownBy(() -> useCase.assign("BKG-2026000001", chosen, null))
                // 型で断る理由を名指しする。素の IllegalStateException にすると、
                // こちら側の不備まで同じ 409 と同じ文言で返ってしまう（IT6 タスク 0.4）
                .isInstanceOf(RouteNoLongerAvailableException.class)
                .hasMessageContaining("もう使えません");

        assertThat(saved).as("断ったのに保存している").isEmpty();
    }

    @Test
    @DisplayName("候補が 1 件も出なくなっていれば、確定を断る")
    void rejectsWhenNothingIsAvailable() {
        CargoItinerary chosen = direct();

        assertThatThrownBy(() -> useCase.assign("BKG-2026000001", chosen, null))
                .isInstanceOf(RouteNoLongerAvailableException.class);
    }

    /**
     * <strong>こちら側の不備は、利用者に作業を促す断り方にしない</strong>（IT6 タスク 0.4）。
     *
     * <p>予約が持つ地点は登録時に検査を通っている。それがマスタから消えているのは
     * 種データか複製の同期の問題（[ADR-014]）であり、経路設計者が何度探し直しても直らない。
     */
    @Test
    @DisplayName("目的地がマスタから消えていたら、経路の不成立とは別の断り方をする")
    void separatesOurOwnDefectFromAnUnavailableRoute() {
        availableCandidates.add(direct());
        destinationZone = null;

        CargoItinerary chosen = direct();

        assertThatThrownBy(() -> useCase.assign("BKG-2026000001", chosen, null))
                .isInstanceOf(LocationMasterMissingException.class)
                .as("経路が使えないという断り方に混ざっている")
                .hasMessageNotContaining("もう使えません");

        assertThat(saved).as("断ったのに保存している").isEmpty();
    }

    /**
     * US10 で緩めた条件を再検証にも渡す。
     *
     * <p>渡さないと、緩めた条件で選んだ経路が「候補に無い」と判定され、
     * 画面には出たのに確定できない。
     */
    @Test
    @DisplayName("候補を出したときの積み替え上限を、再検証にも使う")
    void reverifiesWithTheSameCriteria() {
        availableCandidates.add(viaBusan());

        useCase.assign("BKG-2026000001", viaBusan(), 3);

        assertThat(askedWith).hasSize(1);
        assertThat(askedWith.get(0).maxTransshipments()).isEqualTo(3);
        // 予約の条件をそのまま使う。画面が送った条件で引き直すと、別の予約の条件で
        // 確かめることになる
        assertThat(askedWith.get(0).originUnLocode()).isEqualTo("JPTYO");
        assertThat(askedWith.get(0).destinationUnLocode()).isEqualTo("USLAX");
        assertThat(askedWith.get(0).arrivalDeadline())
                .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
        assertThat(askedWith.get(0).earliestDeparture())
                .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 1));
    }

    @Test
    @DisplayName("予約が見つからなければ空を返す")
    void returnsEmptyForUnknownBooking() {
        availableCandidates.add(direct());

        assertThat(useCase.assign("BKG-9999999999", direct(), null)).isEmpty();
    }

    /**
     * 誤配のあとの組み直し（US28-4・US28-6・[ADR-026] 決定 4・5）。
     *
     * <p>集約と画面には検査があるが、<strong>この橋には無かった</strong>——超過の日数を
     * 常に {@code null} にしても全層が緑になる状態だった（IT10 レビュー・tester 高 3）。
     */
    @Nested
    @DisplayName("誤配のあとの組み直し")
    class WhenRerouting {

        private static final Location SINGAPORE = Location.of("SGSIN", "Singapore");

        private static Cargo misroutedAtSingapore() {
            return requested()
                    .assignItinerary(direct(), ZoneId.of("America/Los_Angeles"))
                    .afterHandling("UNLOAD", "SGSIN", Instant.parse("2030-09-05T09:00:00Z"))
                    .misrouted("SGSIN", Instant.parse("2030-09-05T09:00:00Z"));
        }

        /** 現在地から出て、**期限（9/20）を 5 日超えて着く**旅程。 */
        private static CargoItinerary lateFromSingapore() {
            return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0301"), SINGAPORE,
                    LOS_ANGELES, Instant.parse("2030-09-08T09:00:00Z"),
                    Instant.parse("2030-09-25T09:00:00Z"))));
        }

        @Test
        @DisplayName("現在地を出発地として候補を問い合わせ、期限で弾かないことを伝える")
        void asksFromTheCurrentLocationWithoutTheDeadline() {
            stored = misroutedAtSingapore();
            availableCandidates.add(lateFromSingapore());

            useCase.assign("BKG-2026000001", lateFromSingapore(), null);

            assertThat(askedWith).hasSize(1);
            assertThat(askedWith.get(0).originUnLocode())
                    .as("元の出発地で候補を引いている。選べた候補が確定で断られる")
                    .isEqualTo("SGSIN");
            assertThat(askedWith.get(0).reroute())
                    .as("期限で弾かないことを伝えていない。候補が 1 本も返らない")
                    .isTrue();
        }

        @Test
        @DisplayName("期限を超える分を、日数で返す")
        void reportsHowManyDaysBeyondTheDeadline() {
            stored = misroutedAtSingapore();
            availableCandidates.add(lateFromSingapore());

            assertThat(useCase.assign("BKG-2026000001", lateFromSingapore(), null))
                    .get()
                    .extracting(AssignRouteUseCase.AssignmentResult::daysBeyondDeadline)
                    .as("超過の日数が返っていない。荷主は次の手を決められない")
                    .isEqualTo(5L);
        }

        /** 通常の割り当てでは伝えない。**緩めるのは再設計だけ**。 */
        @Test
        @DisplayName("通常の割り当てでは、期限で弾く既定のままにする")
        void keepsTheDeadlineForOrdinaryAssignment() {
            availableCandidates.add(direct());

            useCase.assign("BKG-2026000001", direct(), null);

            assertThat(askedWith.get(0).reroute()).isFalse();
            assertThat(askedWith.get(0).originUnLocode()).isEqualTo("JPTYO");
        }

        /**
         * <strong>目的地と希望期限は引き継ぐ</strong>（US28-5）。組み直しても荷主との
         * 約束は変わらない——変わったのは出発地だけである。
         */
        @Test
        @DisplayName("目的地と希望期限は、組み直しても変わらない")
        void carriesOverTheDestinationAndDeadline() {
            stored = misroutedAtSingapore();
            availableCandidates.add(lateFromSingapore());

            Cargo reassigned = useCase.assign("BKG-2026000001", lateFromSingapore(), null)
                    .orElseThrow().cargo();

            assertThat(reassigned.routeSpecification().destination()).isEqualTo(LOS_ANGELES);
            assertThat(reassigned.routeSpecification().arrivalDeadline())
                    .as("組み直しで希望期限が動いている。荷主との約束が消える")
                    .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
        }
    }
}
