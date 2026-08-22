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
            return Optional.of(ZoneId.of("America/Los_Angeles"));
        }
    };

    private Cargo stored = requested();

    private final CargoRepository cargoes = new CargoRepository() {
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
        public List<CargoSummary> search(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses, int limit) {
            return List.of();
        }

        @Override
        public long count(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses) {
            return 0;
        }
    };

    private final AssignRouteUseCase useCase =
            new AssignRouteUseCase(cargoes, locations, routeCandidates);

    private static Cargo requested() {
        return Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L,
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

        Cargo assigned = useCase.assign("BKG-2026000001", direct(), null).orElseThrow();

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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("もう使えません");

        assertThat(saved).as("断ったのに保存している").isEmpty();
    }

    @Test
    @DisplayName("候補が 1 件も出なくなっていれば、確定を断る")
    void rejectsWhenNothingIsAvailable() {
        CargoItinerary chosen = direct();

        assertThatThrownBy(() -> useCase.assign("BKG-2026000001", chosen, null))
                .isInstanceOf(IllegalStateException.class);
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
}
