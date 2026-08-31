package com.example.trackingms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingEvent;
import com.example.trackingms.domain.model.entities.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("荷主向け追跡クエリ")
class ShipperTrackingQueryUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final String OWN_NUMBER = "TRK-20260823-0001";
    private static final String OTHER_NUMBER = "TRK-20260823-9001";

    private final InMemoryActivities activities = new InMemoryActivities();
    private final InMemoryLinks links = new InMemoryLinks();
    private final InMemorySnapshots snapshots = new InMemorySnapshots();
    private final ShipperTrackingQueryUseCase useCase =
            new ShipperTrackingQueryUseCase(activities, links, snapshots);

    @Test
    @DisplayName("荷主に紐付いていない利用者には、空配列ではなく未紐付けを返す")
    void returnsUnlinkedWhenTheUserHasNoShipperLink() {
        links.linkedShipperId = Optional.empty();

        ShipperTrackingQueryResult result = useCase.list("shipper01");

        assertThat(result.linked()).isFalse();
        assertThat(result.cargos()).isEmpty();
        assertThat(result.contactMessage()).contains("営業担当");
        assertThat(activities.recentlyListed).isFalse();
    }

    @Test
    @DisplayName("紐付いた荷主の貨物だけを一覧で返す")
    void listsOnlyOwnCargo() {
        links.linkedShipperId = Optional.of(1L);
        activities.stored = List.of(received(OWN_NUMBER), received(OTHER_NUMBER));
        snapshots.items = List.of(
                new ShipperCargoSnapshot("BKG-2026000001", OWN_NUMBER, 1L),
                new ShipperCargoSnapshot("BKG-2026009001", OTHER_NUMBER, 99L));

        ShipperTrackingQueryResult result = useCase.list("shipper01");

        assertThat(result.linked()).isTrue();
        assertThat(result.contactMessage()).isNull();
        assertThat(result.cargos()).extracting(ShipperTrackingSummary::trackingNumber)
                .containsExactly(OWN_NUMBER);
        assertThat(result.cargos().getFirst().statusLabel()).isEqualTo("受領済み");
        assertThat(result.cargos().getFirst().locationName()).isEqualTo("Tokyo");
        assertThat(result.cargos().getFirst().estimatedArrival())
                .isEqualTo(LocalDate.of(2027, Month.SEPTEMBER, 15));
    }

    @Test
    @DisplayName("自社貨物でなければ、追跡が存在しても詳細を返さない")
    void hidesOtherShippersDetail() {
        links.linkedShipperId = Optional.of(1L);
        activities.stored = List.of(received(OTHER_NUMBER));
        snapshots.items = List.of(new ShipperCargoSnapshot("BKG-2026009001", OTHER_NUMBER, 99L));

        Optional<ShipperTrackingDetail> detail = useCase.detail("shipper01", OTHER_NUMBER);

        assertThat(detail).isEmpty();
    }

    @Test
    @DisplayName("自社貨物の詳細には経過を含める")
    void returnsOwnCargoDetailWithEvents() {
        links.linkedShipperId = Optional.of(1L);
        activities.stored = List.of(received(OWN_NUMBER));
        activities.events = List.of(new TrackingEvent(TrackingStatus.RECEIVED, TOKYO,
                Instant.parse("2027-09-02T00:00:00Z"), TrackingEvent.EventSource.HANDLING));
        snapshots.items = List.of(new ShipperCargoSnapshot("BKG-2026000001", OWN_NUMBER, 1L));

        ShipperTrackingDetail detail = useCase.detail("shipper01", OWN_NUMBER).orElseThrow();

        assertThat(detail.trackingNumber()).isEqualTo(OWN_NUMBER);
        assertThat(detail.hasException()).isFalse();
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.events().getFirst().statusLabel()).isEqualTo("受領済み");
    }

    @Test
    @DisplayName("未解決の例外があれば一覧に印を出す")
    void marksActiveException() {
        links.linkedShipperId = Optional.of(1L);
        activities.stored = List.of(received(OWN_NUMBER)
                .raiseException(ExceptionType.LOST, "所在不明", Instant.parse("2027-09-03T00:00:00Z")));
        snapshots.items = List.of(new ShipperCargoSnapshot("BKG-2026000001", OWN_NUMBER, 1L));

        ShipperTrackingSummary summary = useCase.list("shipper01").cargos().getFirst();

        assertThat(summary.hasException()).isTrue();
        assertThat(summary.urgent()).isTrue();
    }

    @Test
    @DisplayName("自社貨物が直近 100 件の外にあっても一覧に出る")
    void listsOwnCargoBeyondTheRecentWindow() {
        links.linkedShipperId = Optional.of(1L);
        List<TrackingActivity> stored = new ArrayList<>();
        List<ShipperCargoSnapshot> items = new ArrayList<>();
        for (int i = 1; i <= ShipperTrackingQueryUseCase.LIST_LIMIT + 1; i++) {
            String number = "TRK-20260823-%04d".formatted(9000 + i);
            stored.add(received(number));
            items.add(new ShipperCargoSnapshot("BKG-202600%04d".formatted(9000 + i), number, 99L));
        }
        stored.add(received(OWN_NUMBER));
        items.add(new ShipperCargoSnapshot("BKG-2026000001", OWN_NUMBER, 1L));
        activities.stored = stored;
        snapshots.items = items;

        ShipperTrackingQueryResult result = useCase.list("shipper01");

        assertThat(result.cargos()).extracting(ShipperTrackingSummary::trackingNumber)
                .containsExactly(OWN_NUMBER);
    }

    @Test
    @DisplayName("一覧は荷主で絞ってから引く。他社の貨物を 1 件ずつ問い合わせない")
    void doesNotAskSnapshotsOneByOne() {
        links.linkedShipperId = Optional.of(1L);
        activities.stored = List.of(received(OWN_NUMBER), received(OTHER_NUMBER));
        snapshots.items = List.of(
                new ShipperCargoSnapshot("BKG-2026000001", OWN_NUMBER, 1L),
                new ShipperCargoSnapshot("BKG-2026009001", OTHER_NUMBER, 99L));

        useCase.list("shipper01");

        assertThat(snapshots.askedByTrackingNumber).isZero();
        assertThat(activities.recentlyListed).isFalse();
    }

    private static TrackingActivity received(String number) {
        return TrackingActivity.start(TrackingNumber.of(number),
                        TrackingBookingId.of("BKG-" + number.substring(4, 12) + number.substring(13)),
                        TOKYO, LOS_ANGELES, LocalDate.of(2027, Month.OCTOBER, 20))
                .afterHandling("RECEIVE", "JPTYO")
                .withEstimatedArrival(LocalDate.of(2027, Month.SEPTEMBER, 15));
    }

    private static final class InMemoryLinks implements UserShipperLinkFinder {
        Optional<Long> linkedShipperId = Optional.of(1L);

        @Override
        public Optional<Long> findLinkedShipperId(String username) {
            return linkedShipperId;
        }
    }

    private static final class InMemorySnapshots implements ShipperCargoSnapshotFinder {
        List<ShipperCargoSnapshot> items = new ArrayList<>();
        int askedByTrackingNumber;

        @Override
        public Optional<ShipperCargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber) {
            askedByTrackingNumber++;
            return items.stream()
                    .filter(item -> item.trackingNumber().equals(trackingNumber.value()))
                    .findFirst();
        }

        @Override
        public List<ShipperCargoSnapshot> findByShipperId(long shipperId) {
            return items.stream().filter(item -> item.shipperId() == shipperId).toList();
        }
    }

    private static final class InMemoryActivities implements TrackingActivityRepository {
        List<TrackingActivity> stored = new ArrayList<>();
        List<TrackingEvent> events = new ArrayList<>();
        boolean recentlyListed;

        @Override
        public TrackingActivity saveIfAbsent(TrackingActivity activity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateStatus(TrackingActivity activity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            return stored.stream()
                    .filter(activity -> activity.trackingNumber().equals(trackingNumber))
                    .findFirst();
        }

        @Override
        public List<TrackingActivity> findRecent(int limit) {
            recentlyListed = true;
            return stored.stream().limit(limit).toList();
        }

        @Override
        public List<TrackingActivity> findByTrackingNumbers(
                java.util.Collection<TrackingNumber> trackingNumbers) {
            return stored.stream().filter(activity ->
                    trackingNumbers.contains(activity.trackingNumber())).toList();
        }

        @Override
        public void appendEvent(TrackingNumber trackingNumber, TrackingEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrackingEvent> findEvents(TrackingNumber trackingNumber, int limit) {
            return events.stream().limit(limit).toList();
        }

        @Override
        public void saveException(TrackingNumber trackingNumber, TrackingActivity activity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrackingActivity> findWithOpenExceptions(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrackingExceptionEvent> findExceptions(TrackingNumber trackingNumber,
                int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
