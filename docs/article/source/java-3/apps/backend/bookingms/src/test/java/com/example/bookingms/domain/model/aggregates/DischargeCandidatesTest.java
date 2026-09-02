package com.example.bookingms.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.bookingms.domain.model.valueobjects.CargoStatus;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;
import com.example.bookingms.domain.model.valueobjects.TransportStatus;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;

/**
 * 陸揚げ地の候補（US30-5・[ADR-025] 決定 4）。
 *
 * <p><strong>全港から選ばせない。</strong>船が寄らない港を指定できると、荷降しできない
 * 約束を荷主にすることになる。
 *
 * <p>候補は <strong>bookingms の中で作る</strong>。現在地は荷役のイベントが運び、
 * 次の寄港地は旅程が持っている——どちらも自分の手元にある。trackingms へ引くと、
 * 同じ事実を 2 ホップ先から取りに行くことになる。
 */
@DisplayName("陸揚げ地の候補")
class DischargeCandidatesTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Location HONG_KONG = Location.of("HKHKG", "Hong Kong");
    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");

    private static Cargo tracked() {
        CargoItinerary itinerary = CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0201"), TOKYO, SHANGHAI,
                        Instant.parse("2026-09-02T09:00:00Z"),
                        Instant.parse("2026-09-05T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0202"), SHANGHAI, LOS_ANGELES,
                        Instant.parse("2026-09-06T09:00:00Z"),
                        Instant.parse("2026-09-18T09:00:00Z"))));

        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.TRACKING_ISSUED, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)),
                itinerary, null, TrackingNumber.of("TRK-20260823-0001"));
    }

    /** まだ荷役が起きていなければ、現在地は分からない。旅程の荷降し地だけが候補になる。 */
    @Test
    @DisplayName("荷役が無ければ、旅程の荷降し地だけが候補になる")
    void offersOnlyTheItineraryPortsBeforeAnyHandling() {
        assertThat(tracked().dischargeCandidates())
                .extracting(DischargeCandidate::unLocode)
                .containsExactly("CNSHA", "USLAX");
    }

    /** <strong>現在地の港が先に来る。</strong>いま貨物がある場所が、最も早く降ろせる。 */
    @Test
    @DisplayName("現在地の港が候補の先頭に来る")
    void putsTheCurrentPortFirst() {
        Cargo loaded = tracked().afterHandling("LOAD", "CNSHA", AT);

        assertThat(loaded.dischargeCandidates())
                .extracting(DischargeCandidate::unLocode)
                .containsExactly("CNSHA", "USLAX");
        assertThat(loaded.dischargeCandidates().getFirst().reason()).isEqualTo("現在地の港");
    }

    /**
     * <strong>なぜ候補なのかを添える。</strong>
     *
     * <p>港の名前だけを並べると、追跡管理者はどれを選べばよいか決められない。
     */
    @Test
    @DisplayName("候補には、なぜ候補なのかが添えられる")
    void explainsWhyEachPortIsACandidate() {
        Cargo loaded = tracked().afterHandling("LOAD", "CNSHA", AT);

        assertThat(loaded.dischargeCandidates())
                .extracting(DischargeCandidate::reason)
                .containsExactly("現在地の港", "次の寄港地");
    }

    /** 同じ港が現在地でも寄港地でも、候補には 1 度しか出さない。 */
    @Test
    @DisplayName("同じ港を二重に出さない")
    void doesNotRepeatTheSamePort() {
        Cargo loaded = tracked().afterHandling("UNLOAD", "CNSHA", AT);

        assertThat(loaded.dischargeCandidates())
                .extracting(DischargeCandidate::unLocode)
                .doesNotHaveDuplicates();
    }

    /**
     * <strong>候補外の港での承認は断る。</strong>
     *
     * <p>船が寄らない港を指定できると、荷降しできない約束を荷主にすることになる。
     */
    @Test
    @DisplayName("候補に無い港は選べない")
    void rejectsAPortOutsideTheCandidates() {
        Cargo loaded = tracked().afterHandling("LOAD", "CNSHA", AT);

        assertThat(loaded.canDischargeAt("CNSHA")).isTrue();
        assertThat(loaded.canDischargeAt("USLAX")).isTrue();
        assertThat(loaded.canDischargeAt("JPYOK"))
                .as("旅程に無い港を選べている。荷降しできない約束を荷主にすることになる")
                .isFalse();
    }


    /**
     * <strong>通過済みの港は候補に出さない。</strong>
     *
     * <p>旅程の全区間から採ると、すでに通り過ぎた港が並ぶ。「近いから」と選んで承認すると、
     * <strong>船が二度と寄らない港で荷降しする約束</strong>を荷主にすることになる
     * ——この候補方式が防ごうとしていた事故そのものである。
     */
    @Test
    @DisplayName("すでに通り過ぎた港は候補に出ない")
    void leavesOutThePortsAlreadyPassed() {
        Cargo viaHongKong = viaHongKong();

        // 香港を出て上海に着き、そこで積み込んだ（次は Los Angeles）
        Cargo loadedAtShanghai = viaHongKong.afterHandling("LOAD", "CNSHA",
                Instant.parse("2026-09-08T00:00:00Z"));

        assertThat(loadedAtShanghai.dischargeCandidates())
                .extracting(DischargeCandidate::unLocode)
                .as("通り過ぎた香港が候補に出ている。船が二度と寄らない港を選べてしまう")
                .containsExactly("CNSHA", "USLAX");
        assertThat(loadedAtShanghai.canDischargeAt("HKHKG"))
                .as("通り過ぎた港での承認が通っている")
                .isFalse();
    }

    /** 荷役が起きる前は、旅程のすべてがこれからである。 */
    @Test
    @DisplayName("荷役の前なら、旅程の港はすべて候補になる")
    void offersEveryPortBeforeAnyHandling() {
        assertThat(viaHongKong().dischargeCandidates())
                .extracting(DischargeCandidate::unLocode)
                .containsExactly("HKHKG", "CNSHA", "USLAX");
    }

    /** 東京 → 香港 → 上海 → ロサンゼルスの旅程。 */
    private static Cargo viaHongKong() {
        CargoItinerary itinerary = CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0301"), TOKYO, HONG_KONG,
                        Instant.parse("2026-09-02T09:00:00Z"),
                        Instant.parse("2026-09-05T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0302"), HONG_KONG, SHANGHAI,
                        Instant.parse("2026-09-06T09:00:00Z"),
                        Instant.parse("2026-09-07T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0303"), SHANGHAI, LOS_ANGELES,
                        Instant.parse("2026-09-09T09:00:00Z"),
                        Instant.parse("2026-09-18T09:00:00Z"))));

        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.TRACKING_ISSUED, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)),
                itinerary, null, TrackingNumber.of("TRK-20260823-0001"));
    }

    /** 旅程が無ければ候補も無い。経路が決まる前のキャンセルに承認は要らない。 */
    @Test
    @DisplayName("旅程が無ければ候補も無い")
    void hasNoCandidatesWithoutAnItinerary() {
        Cargo booked = Cargo.book(1L,
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)));

        assertThat(booked.dischargeCandidates()).isEmpty();
        assertThat(booked.canDischargeAt("USLAX")).isFalse();
    }
}
