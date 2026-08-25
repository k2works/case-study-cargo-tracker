package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.port.CancellationRequestRepository;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.CancellationRequest;
import com.example.bookingms.domain.model.CancellationStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoRestoration;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TrackingNumber;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Clock;
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
 * キャンセルの申請と承認（US30・[ADR-025] 決定 4）。
 *
 * <p>ここが守るのは<strong>承認を迂回する経路が無いこと</strong>である。
 * 輸送中の貨物が、追跡管理者の判断を経ずに止まってはいけない。
 */
@DisplayName("キャンセルの申請と承認")
class CancellationUseCaseTest {

    private static final String BOOKING_ID = "BKG-2026000001";
    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneId.of("Asia/Tokyo"));

    private final List<CancellationRequest> requests = new ArrayList<>();
    private Cargo stored;

    private final CargoRepository cargoes = new StubCargoes();
    private final CancellationRequestRepository cancellations = new StubCancellations();

    private final RequestCancellationUseCase request =
            new RequestCancellationUseCase(cargoes, cancellations, clock);
    /** 発行されたキャンセルのイベント。**発行したことを検査から見る**。 */
    private final List<com.example.bookingms.application.port.CargoCancelled> published =
            new ArrayList<>();

    private final com.example.bookingms.application.port.CargoEventNotifier events =
            new com.example.bookingms.application.port.CargoEventNotifier() {
                @Override
                public void trackingNumberIssued(
                        com.example.bookingms.application.port.TrackingNumberIssued event) {
                    throw new UnsupportedOperationException("この検査では使わない");
                }

                @Override
                public void cargoCancelled(
                        com.example.bookingms.application.port.CargoCancelled event) {
                    published.add(event);
                }
            };

    private final DecideCancellationUseCase decide =
            new DecideCancellationUseCase(cargoes, cancellations, events, clock);

    private static Cargo cargoAt(BookingStatus status, String lastPort) {
        CargoItinerary itinerary = CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0201"), TOKYO, SHANGHAI,
                        Instant.parse("2026-09-02T09:00:00Z"),
                        Instant.parse("2026-09-05T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0202"), SHANGHAI, LOS_ANGELES,
                        Instant.parse("2026-09-06T09:00:00Z"),
                        Instant.parse("2026-09-18T09:00:00Z"))));

        return CargoRestoration.restore(1L, BookingId.of(BOOKING_ID), 1L,
                new CargoStatus(status, TransportStatus.NOT_RECEIVED, RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)),
                itinerary, null, TrackingNumber.of("TRK-20260823-0001"),
                lastPort, Instant.parse("2026-09-05T00:00:00Z"));
    }

    @Nested
    @DisplayName("申請するとき（US30-1〜3）")
    class WhenRequesting {

        /** US30-2。輸送開始前は承認を待たずに確定する。 */
        @Test
        @DisplayName("輸送開始前の申請は、その場でキャンセルが確定する")
        void settlesImmediatelyBeforeDeparture() {
            stored = cargoAt(BookingStatus.CONFIRMED, null);

            CancellationOutcome outcome = request.request(BOOKING_ID, "荷主都合", "sales01");

            assertThat(outcome.awaitingApproval()).isFalse();
            assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        /** US30-3。輸送中は承認を待つ。**予約はまだ止まらない**。 */
        @Test
        @DisplayName("輸送中の申請は承認待ちになり、予約は輸送中のまま")
        void awaitsApprovalWhileInTransit() {
            stored = cargoAt(BookingStatus.IN_TRANSIT, "CNSHA");

            CancellationOutcome outcome = request.request(BOOKING_ID, "荷主都合", "sales01");

            assertThat(outcome.awaitingApproval()).isTrue();
            assertThat(outcome.request().status()).isEqualTo(CancellationStatus.REQUESTED);
            assertThat(stored.bookingStatus())
                    .as("承認を待たずに予約が止まっている。承認の意味が無くなる")
                    .isEqualTo(BookingStatus.IN_TRANSIT);
        }

        /** 判断待ちの申請は貨物あたり 1 件まで。2 件あると、どちらを承認するか決まらない。 */
        @Test
        @DisplayName("承認待ちの申請があるあいだは、2 通目を断る")
        void rejectsASecondRequestWhileAwaiting() {
            stored = cargoAt(BookingStatus.IN_TRANSIT, "CNSHA");
            request.request(BOOKING_ID, "荷主都合", "sales01");

            assertThatThrownBy(() -> request.request(BOOKING_ID, "やっぱり止める", "sales01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("承認待ち");
            assertThat(requests).hasSize(1);
        }

        @Test
        @DisplayName("知らない予約番号は断る")
        void rejectsAnUnknownBooking() {
            stored = cargoAt(BookingStatus.IN_TRANSIT, "CNSHA");

            assertThatThrownBy(() -> request.request("BKG-9999999999", "荷主都合", "sales01"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("承認するとき（US30-5）")
    class WhenApproving {

        private CancellationRequest awaiting() {
            stored = cargoAt(BookingStatus.IN_TRANSIT, "CNSHA");
            return request.request(BOOKING_ID, "荷主都合", "sales01").request();
        }

        @Test
        @DisplayName("承認すると、キャンセルが確定して陸揚げ地が残る")
        void settlesTheCancellation() {
            awaiting();

            CancellationRequest approved =
                    decide.approve(BOOKING_ID, "CNSHA", "tracker01", "荷主と合意");

            assertThat(approved.status()).isEqualTo(CancellationStatus.APPROVED);
            assertThat(approved.dischargeLocation()).contains("CNSHA");
            assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        /**
         * <strong>[ADR-025] 決定 4。候補外の港での承認は断る。</strong>
         *
         * <p>船が寄らない港を指定できると、荷降しできない約束を荷主にすることになる。
         * <strong>判定は集約が持つ</strong>——ここで旅程を見に行くと規則が 2 か所に分かれる。
         */
        @Test
        @DisplayName("候補に無い港での承認は断る")
        void rejectsAPortOutsideTheCandidates() {
            awaiting();

            assertThatThrownBy(() ->
                    decide.approve(BOOKING_ID, "JPYOK", "tracker01", "荷主と合意"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷降し");
            assertThat(stored.bookingStatus())
                    .as("断ったのに予約がキャンセルされている")
                    .isEqualTo(BookingStatus.IN_TRANSIT);
        }

        /**
         * <strong>承認したら知らせる</strong>（[ADR-025] 決定 3）。
         *
         * <p>公開追跡が開いているため、知らせないと荷主は自分が申し入れて承認された
         * キャンセルを画面で否定される。<strong>理由は載せない</strong>——認証の無い画面へ
         * 社内の判断を流さない。
         */
        @Test
        @DisplayName("承認すると、キャンセルが確定したことを知らせる")
        void announcesTheCancellation() {
            awaiting();

            decide.approve(BOOKING_ID, "CNSHA", "tracker01", "荷主と合意");

            assertThat(published).hasSize(1);
            assertThat(published.getFirst().trackingNumber()).isEqualTo("TRK-20260823-0001");
            assertThat(published.getFirst().bookingId()).isEqualTo(BOOKING_ID);
        }

        /**
         * <strong>却下では知らせない。</strong>
         *
         * <p>却下は「キャンセルしない」という決定である。知らせると、荷主の画面に
         * キャンセルのお知らせが出て、実際には輸送が続いていることと食い違う。
         */
        @Test
        @DisplayName("却下したときは、キャンセルを知らせない")
        void doesNotAnnounceOnRejection() {
            awaiting();

            decide.reject(BOOKING_ID, "tracker01", "積み替え済みのため");

            assertThat(published)
                    .as("却下したのにキャンセルを知らせている。荷主の画面と実態が食い違う")
                    .isEmpty();
        }

        /** US30-7。**却下しても予約は輸送中のまま。** */
        @Test
        @DisplayName("却下しても、予約は輸送中のまま維持される")
        void keepsTheBookingInTransitOnRejection() {
            awaiting();

            CancellationRequest rejected =
                    decide.reject(BOOKING_ID, "tracker01", "積み替え済みのため");

            assertThat(rejected.status()).isEqualTo(CancellationStatus.REJECTED);
            assertThat(stored.bookingStatus()).isEqualTo(BookingStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("承認待ちの申請が無ければ断る")
        void rejectsDecidingWithoutARequest() {
            stored = cargoAt(BookingStatus.IN_TRANSIT, "CNSHA");

            assertThatThrownBy(() ->
                    decide.approve(BOOKING_ID, "CNSHA", "tracker01", "荷主と合意"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private final class StubCargoes implements CargoRepository {

        @Override
        public Cargo save(Cargo cargo) {
            stored = cargo;
            return cargo;
        }

        @Override
        public Optional<Cargo> findById(Long id) {
            return Optional.ofNullable(stored);
        }

        @Override
        public Optional<CargoSummary> findByBookingId(String bookingId) {
            return BOOKING_ID.equals(bookingId)
                    ? Optional.of(new CargoSummary(stored, "荷主")) : Optional.empty();
        }

        @Override
        public Optional<CargoSummary> findByTrackingNumber(String trackingNumber) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<CargoSummary> search(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses, BookingStatus bookingStatus,
                int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public String nextTrackingNumber() {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public long count(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses, BookingStatus bookingStatus) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    }

    private final class StubCancellations implements CancellationRequestRepository {

        @Override
        public CancellationRequest save(CancellationRequest request) {
            requests.add(request);
            return request;
        }

        @Override
        public CancellationRequest updateDecision(CancellationRequest request) {
            requests.replaceAll(candidate ->
                    candidate.cargoId().equals(request.cargoId()) ? request : candidate);
            return request;
        }

        @Override
        public Optional<CancellationRequest> findAwaitingByCargoId(Long cargoId) {
            return requests.stream().filter(CancellationRequest::awaitingDecision).findFirst();
        }

        @Override
        public Optional<CancellationRequest> findLatestByCargoId(Long cargoId) {
            return requests.isEmpty() ? Optional.empty()
                    : Optional.of(requests.getLast());
        }

        /** 履歴は**新しい順**（US30-10）。本物の SQL と同じ向きで返す。 */
        @Override
        public java.util.List<CancellationRequest> findAllByCargoId(Long cargoId) {
            return requests.reversed();
        }

        @Override
        public List<CancellationRequest> findAwaitingDecision(int limit) {
            return requests.stream().filter(CancellationRequest::awaitingDecision).toList();
        }
    }
}
