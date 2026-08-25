package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.shared.domain.model.Location;

/**
 * 誤配の記録（US28-2・US28-8・[ADR-026] 決定 3）。
 *
 * <p><strong>状態と事実を分けて持つ。</strong>経路の状況は再設計で {@code ROUTED} へ
 * 戻るが、<strong>誤配が起きた事実は戻らない</strong>——料金調整の根拠として参照される。
 */
@DisplayName("誤配の記録")
class MisrouteTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");

    private static Cargo inTransit() {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.IN_TRANSIT, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)),
                CargoItinerary.of(List.of(
                        Leg.of(VoyageNumber.of("V0201"), TOKYO, LOS_ANGELES,
                                Instant.parse("2026-09-02T09:00:00Z"),
                                Instant.parse("2026-09-18T09:00:00Z")))),
                null, TrackingNumber.of("TRK-20260823-0001"));
    }

    @Test
    @DisplayName("予定ルート外の荷役で、経路の状況が誤配になる")
    void marksTheRoutingStatusAsMisrouted() {
        Cargo misrouted = inTransit().misrouted("SGSIN", AT);

        assertThat(misrouted.routingStatus()).isEqualTo(RoutingStatus.MISROUTED);
        assertThat(misrouted.isMisrouted()).isTrue();
    }

    /**
     * <strong>どこで外れたかまでが事実である</strong>（受入基準 28-3）。
     *
     * <p>「誤配があった」だけでは、荷主にも経理にも説明できない。
     */
    @Test
    @DisplayName("いつ・どこで外れたかを残す")
    void recordsWhenAndWhere() {
        Cargo misrouted = inTransit().misrouted("SGSIN", AT);

        assertThat(misrouted.misroute()).isPresent();
        assertThat(misrouted.misroute().orElseThrow().locationUnLocode()).isEqualTo("SGSIN");
        assertThat(misrouted.misroute().orElseThrow().at()).isEqualTo(AT);
    }

    /**
     * <strong>2 回目以降は最初の誤配を残す。</strong>
     *
     * <p>誤配のあと目的地へ向かわずに別の港でも荷役が起きることはある。
     * <strong>いつ経路から外れたか</strong>が料金調整の起点であり、最後に外れた場所ではない。
     */
    @Test
    @DisplayName("2 回目の誤配でも、最初に外れた場所と日時を残す")
    void keepsTheFirstMisroute() {
        Cargo twice = inTransit()
                .misrouted("SGSIN", AT)
                .misrouted("HKHKG", Instant.parse("2026-09-08T00:00:00Z"));

        assertThat(twice.misroute().orElseThrow().locationUnLocode())
                .as("最後に外れた場所で上書きされている。料金調整の起点が動く")
                .isEqualTo("SGSIN");
        assertThat(twice.misroute().orElseThrow().at()).isEqualTo(AT);
    }

    /**
     * <strong>キャンセル済みの予約は動かさない。</strong>
     *
     * <p>遅れて届いた荷役でキャンセルが覆ると、荷主との約束と記録が食い違う。
     */
    @Test
    @DisplayName("キャンセル済みの予約は誤配にしない")
    void doesNotTouchCancelledCargo() {
        Cargo cancelled = inTransit().cancel();

        Cargo unchanged = cancelled.misrouted("SGSIN", AT);

        assertThat(unchanged.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(unchanged.isMisrouted())
                .as("キャンセル済みの予約が誤配になっている。約束と記録が食い違う")
                .isFalse();
    }

    @Nested
    @DisplayName("誤配の事実そのもの")
    class TheRecordItself {

        @Test
        @DisplayName("日時の無い誤配は作れない")
        void requiresATime() {
            assertThatThrownBy(() -> new Misroute(null, "SGSIN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 「誤配があった」だけでは、荷主にも経理にも説明できない。 */
        @Test
        @DisplayName("港の無い誤配は作れない")
        void requiresAPort() {
            assertThatThrownBy(() -> new Misroute(AT, " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("誤配のあとに経路を組み直すとき（US28-4・[ADR-026] 決定 4b）")
    class WhenReassigning {

        private static final Location SINGAPORE = Location.of("SGSIN", "Singapore");

        /** 現在地から目的地へ向かう旅程。**出発地は元の東京ではなくシンガポール**。 */
        private static CargoItinerary fromSingapore() {
            return CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0301"), SINGAPORE, LOS_ANGELES,
                            Instant.parse("2026-09-08T09:00:00Z"),
                            Instant.parse("2026-09-19T09:00:00Z"))));
        }

        private static Cargo misroutedAtSingapore() {
            return inTransit().afterHandling("UNLOAD", "SGSIN", AT).misrouted("SGSIN", AT);
        }

        /**
         * <strong>確定した記録を消さない</strong>（[ADR-021] 決定 3・[ADR-026] 決定 4b）。
         *
         * <p>通常の割り当てを通すと、輸送中の貨物が {@code ROUTE_PROPOSED} へ戻る
         * ——荷主が合意して確定した記録が消え、追跡番号を持つ貨物が「経路を提示した」
         * 状態になる。
         */
        @Test
        @DisplayName("予約の状態は輸送中のまま、経路の状況だけが戻る")
        void keepsTheBookingStatusWhileRestoringTheRouting() {
            Cargo reassigned = misroutedAtSingapore()
                    .reassignItinerary(fromSingapore(), java.time.ZoneId.of("America/Los_Angeles"));

            assertThat(reassigned.bookingStatus())
                    .as("輸送中の貨物が経路提示へ戻っている。荷主が合意した記録が消える")
                    .isEqualTo(BookingStatus.IN_TRANSIT);
            assertThat(reassigned.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        }

        /**
         * <strong>誤配の事実は消さない</strong>（受入基準 28-8）。
         *
         * <p>組み直した瞬間に消えると、料金調整の根拠が失われる。
         */
        @Test
        @DisplayName("組み直しても、誤配の記録は残る")
        void keepsTheMisrouteRecord() {
            Cargo reassigned = misroutedAtSingapore()
                    .reassignItinerary(fromSingapore(), java.time.ZoneId.of("America/Los_Angeles"));

            assertThat(reassigned.isMisrouted())
                    .as("組み直した瞬間に誤配の記録が消えている。料金調整の根拠が失われる")
                    .isTrue();
            assertThat(reassigned.misroute().orElseThrow().locationUnLocode()).isEqualTo("SGSIN");
        }

        /**
         * <strong>出発地は現在地である</strong>（US28-4）。
         *
         * <p>元の出発地から組んだ経路は、貨物が今いない港からの経路である——現場は動けない。
         */
        @Test
        @DisplayName("元の出発地から組んだ経路は使えない")
        void rejectsAnItineraryFromTheOriginalOrigin() {
            CargoItinerary fromTokyo = CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0401"), TOKYO, LOS_ANGELES,
                            Instant.parse("2026-09-08T09:00:00Z"),
                            Instant.parse("2026-09-19T09:00:00Z"))));

            assertThatThrownBy(() -> misroutedAtSingapore()
                    .reassignItinerary(fromTokyo, java.time.ZoneId.of("America/Los_Angeles")))
                    .as("貨物が今いない港からの経路が通っている。現場は動けない")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("現在地");
        }

        /** <strong>目的地は引き継ぐ</strong>（受入基準 28-5）。荷主との約束は変わっていない。 */
        @Test
        @DisplayName("目的地が違う経路は使えない")
        void rejectsAnItineraryToAnotherDestination() {
            Location shanghai = Location.of("CNSHA", "Shanghai");
            CargoItinerary toShanghai = CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0501"), SINGAPORE, shanghai,
                            Instant.parse("2026-09-08T09:00:00Z"),
                            Instant.parse("2026-09-10T09:00:00Z"))));

            assertThatThrownBy(() -> misroutedAtSingapore()
                    .reassignItinerary(toShanghai, java.time.ZoneId.of("America/Los_Angeles")))
                    .as("目的地が変わっている。荷主との約束は変わっていない")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>期限を超えるなら、何日超えるかが分かる</strong>（US28-6・[ADR-026] 決定 5）。
         *
         * <p>「間に合いません」だけでは、荷主は次の手を決められない
         * ——1 日なのか 2 週間なのかで判断は変わる。
         */
        @Test
        @DisplayName("期限を超える経路では、超える日数が分かる")
        void tellsHowManyDaysBeyondTheDeadline() {
            // 期限は 2026-09-20。到着は 2026-09-25（目的地の暦で 5 日超過）
            CargoItinerary late = CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0601"), SINGAPORE, LOS_ANGELES,
                            Instant.parse("2026-09-10T09:00:00Z"),
                            Instant.parse("2026-09-25T20:00:00Z"))));
            Cargo reassigned = misroutedAtSingapore()
                    .reassignItinerary(late, java.time.ZoneId.of("America/Los_Angeles"));

            assertThat(reassigned.daysBeyondDeadline(java.time.ZoneId.of("America/Los_Angeles")))
                    .as("何日超えるかが分からない。荷主は次の手を決められない")
                    .contains(5L);
        }

        /**
         * <strong>判断は目的地の暦で行う</strong>（[ADR-017]）。
         *
         * <p>UTC で判断すると、時差の分だけ超過日数が増減する。ロサンゼルスは UTC より
         * 遅れているため、<strong>UTC で 9/21 早朝の到着は、現地ではまだ 9/20</strong>
         * ——期限内である。
         */
        @Test
        @DisplayName("期限当日の到着は、目的地の暦で判断して超過にしない")
        void judgesTheDeadlineInTheDestinationCalendar() {
            CargoItinerary onTime = CargoItinerary.of(List.of(
                    Leg.of(VoyageNumber.of("V0701"), SINGAPORE, LOS_ANGELES,
                            Instant.parse("2026-09-10T09:00:00Z"),
                            // UTC では 9/21、ロサンゼルス（UTC-7）では 9/20 の 20 時
                            Instant.parse("2026-09-21T03:00:00Z"))));
            Cargo reassigned = misroutedAtSingapore()
                    .reassignItinerary(onTime, java.time.ZoneId.of("America/Los_Angeles"));

            assertThat(reassigned.daysBeyondDeadline(java.time.ZoneId.of("America/Los_Angeles")))
                    .as("UTC で判断している。時差の分だけ期限内の便が超過扱いになる")
                    .isEmpty();
        }

        /** 誤配していない予約には使わない。**通常の割り当てが通るべき経路である**。 */
        @Test
        @DisplayName("誤配していない予約には使えない")
        void refusesWhenNotMisrouted() {
            assertThatThrownBy(() -> inTransit()
                    .reassignItinerary(fromSingapore(), java.time.ZoneId.of("America/Los_Angeles")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
