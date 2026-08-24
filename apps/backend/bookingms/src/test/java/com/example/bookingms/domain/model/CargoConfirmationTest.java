package com.example.bookingms.domain.model;

import static com.example.bookingms.domain.model.CargoFixtures.ROUTE;
import static com.example.bookingms.domain.model.CargoFixtures.specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 荷主に提示してから追跡番号を渡すまでの貨物予約（US12〜US14・[ADR-021]）。
 *
 * <p>経路の割り当て（{@link CargoItineraryLifecycleTest}）と分けたのは、確かめている
 * ものが違うからである。ここで見るのは<strong>合意で固まっていく道筋</strong>で、
 * 固まったあとに変えられなくなるものまでを含む。
 */
@DisplayName("貨物予約（通知から追跡番号の発行まで）")
class CargoConfirmationTest {

    /**
     * 荷主への通知・確定・追跡番号の発行（US12〜US14・[ADR-021]）。
     */
    @Nested
    @DisplayName("荷主に提示してから追跡番号を渡すまで")
    class WhenNotifiedAndConfirmed {

        private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
        private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
        private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
        private static final Instant NOTIFIED_AT = Instant.parse("2026-08-22T02:00:00Z");

        private static CargoItinerary valid() {
            return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"), TOKYO, LOS_ANGELES,
                    Instant.parse("2026-09-01T09:00:00Z"),
                    Instant.parse("2026-09-15T12:00:00Z"))));
        }

        private static Cargo proposed() {
            return Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                    .requestRouting()
                    .assignItinerary(valid(), LA);
        }

        private static Cargo notified() {
            return proposed().notifyShipper(NOTIFIED_AT, "sales01");
        }

        /** 決定 1: 通知を状態にする。 */
        @Test
        @DisplayName("通知すると ROUTE_NOTIFIED になり、いつ・誰が が残る")
        void notifyingMovesStatusAndRecords() {
            Cargo cargo = notified();

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.ROUTE_NOTIFIED);
            assertThat(cargo.routeNotification())
                    .contains(RouteNotification.of(NOTIFIED_AT, "sales01"));
            // 経路の状態は動かさない。経路設計は終わっている
            assertThat(cargo.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        }

        @Test
        @DisplayName("経路が決まっていない予約は通知できない")
        void cannotNotifyBeforeRouteIsAssigned() {
            Cargo preliminary = Cargo.book(1L, specification(CargoType.GENERAL, null, null),
                    ROUTE);

            assertThatThrownBy(() -> preliminary.notifyShipper(NOTIFIED_AT, "sales01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("経路");
        }

        /** 決定 2: 再通知を許し、記録は最新で上書きする。 */
        @Test
        @DisplayName("もう一度通知でき、記録は最新で上書きされる")
        void allowsRenotification() {
            Instant later = Instant.parse("2026-08-23T02:00:00Z");

            Cargo renotified = notified().notifyShipper(later, "sales02");

            assertThat(renotified.bookingStatus()).isEqualTo(BookingStatus.ROUTE_NOTIFIED);
            assertThat(renotified.routeNotification())
                    .contains(RouteNotification.of(later, "sales02"));
        }

        /** 決定 1: 通知していない予約は確定できない。 */
        @Test
        @DisplayName("通知していない予約は確定できない")
        void cannotConfirmWithoutNotifying() {
            // 確定は「荷主の合意を得た」という業務上の事実である。提示していない条件で
            // 合意は成り立たない
            assertThatThrownBy(proposed()::confirm)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("通知");
        }

        @Test
        @DisplayName("通知した予約は確定でき、経路の状態は動かない")
        void confirmsAfterNotification() {
            Cargo confirmed = notified().confirm();

            assertThat(confirmed.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(confirmed.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        }

        @Test
        @DisplayName("確定した予約は二重に確定できない")
        void cannotConfirmTwice() {
            assertThatThrownBy(notified().confirm()::confirm)
                    .isInstanceOf(IllegalStateException.class);
        }

        /** 決定 7: 確定しても経路設計者に見えたまま。US14 が 404 にならない根拠。 */
        @Test
        @DisplayName("確定した予約も経路設計者に見える（追跡番号を発行するのは経路設計者）")
        void confirmedCargoStaysVisibleToRoutingPlanner() {
            assertThat(notified().confirm().visibleToRoutingPlanner()).isTrue();
        }

        /** 決定 4: 戻すと RoutingStatus も戻り、旅程は残る。 */
        @Test
        @DisplayName("荷主が変更を希望したら経路設計へ戻せる。旅程は残る")
        void returnsToRouting() {
            Cargo returned = notified().returnToRouting();

            assertThat(returned.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
            // BookingStatus だけ戻しても経路設計者の作業待ちに現れない
            assertThat(returned.routingStatus()).isEqualTo(RoutingStatus.ROUTING_REQUESTED);
            assertThat(returned.awaitingRouting()).isTrue();
            // 見直しの起点になる。どこが気に入られなかったかを、いまの経路を見ながら話す
            assertThat(returned.itinerary()).contains(valid());
        }

        /**
         * <strong>戻した予約を、経路設計者が触る前に通知できてはいけない</strong>
         * （IT6 レビュー・user-representative 指摘）。
         *
         * <p>`returnToRouting` は `BookingStatus` を `ROUTE_PROPOSED` に戻す。通知の可否を
         * `BookingStatus` だけで見ると、<strong>荷主が「この経路は困る」と言って戻した予約を、
         * 同じ経路のまま通知済 → 確定にできる</strong>。荷役はその予定で動き、荷主は違う話を
         * 聞いている状態になる。
         *
         * <p>通知できるのは<strong>いま経路が決まっている</strong>予約だけである。
         */
        @Test
        @DisplayName("経路設計へ戻した予約は、経路が決まり直すまで通知できない")
        void cannotNotifyWhileTheRouteIsBackWithThePlanner() {
            Cargo returned = notified().returnToRouting();

            assertThatThrownBy(() -> returned.notifyShipper(NOTIFIED_AT, "sales01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("経路");
        }

        @Test
        @DisplayName("経路が決まり直せば、また通知できる")
        void canNotifyAgainOnceTheRouteIsReassigned() {
            Cargo reassigned = notified().returnToRouting().assignItinerary(valid(), LA);

            assertThatCode(() -> reassigned.notifyShipper(NOTIFIED_AT, "sales01"))
                    .doesNotThrowAnyException();
        }

        /** 決定 3: 確定したあとは戻せない。 */
        @Test
        @DisplayName("確定した予約は経路設計へ戻せない")
        void cannotReturnToRoutingAfterConfirmation() {
            // 戻せるようにすると、追跡番号が発行された予約の経路が黙って変わり、
            // 荷役の担当者と荷主が別の予定を見る
            assertThatThrownBy(notified().confirm()::returnToRouting)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("通知していない予約は経路設計へ戻せない（戻す先が同じ状態になる）")
        void cannotReturnToRoutingBeforeNotifying() {
            assertThatThrownBy(proposed()::returnToRouting)
                    .isInstanceOf(IllegalStateException.class);
        }

        /** US14-1・US14-3。 */
        @Test
        @DisplayName("確定した予約に追跡番号を発行すると、貨物が受領待ちになる")
        void issuesTrackingNumber() {
            Cargo issued = notified().confirm()
                    .issueTrackingNumber(TrackingNumber.of("TRK-20260822-0001"));

            assertThat(issued.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
            assertThat(issued.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
            assertThat(issued.trackingNumber())
                    .contains(TrackingNumber.of("TRK-20260822-0001"));
        }

        @Test
        @DisplayName("確定していない予約に追跡番号は発行できない")
        void cannotIssueTrackingNumberBeforeConfirmation() {
            Cargo notified = notified();
            TrackingNumber number = TrackingNumber.of("TRK-20260822-0001");

            assertThatThrownBy(() -> notified.issueTrackingNumber(number))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("確定");
        }

        @Test
        @DisplayName("追跡番号を二重に発行できない")
        void cannotIssueTrackingNumberTwice() {
            Cargo issued = notified().confirm()
                    .issueTrackingNumber(TrackingNumber.of("TRK-20260822-0001"));

            TrackingNumber another = TrackingNumber.of("TRK-20260822-0002");

            assertThatThrownBy(() -> issued.issueTrackingNumber(another))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * <strong>差し替えは確定を裏口から取り消せてはいけない</strong>（[ADR-021] 決定 3）。
         *
         * <p>経路の差し替え（[ADR-020] 決定 4）は `RoutingStatus` だけを見ており、確定済みの
         * 予約でも通ってしまう。通ると `BookingStatus` が `ROUTE_PROPOSED` に戻り、
         * <strong>荷主が合意した記録が黙って消える</strong>。しかも確定から戻すことは
         * 決定 3 で禁じたはずである。
         */
        @Test
        @DisplayName("確定した予約の経路は差し替えられない")
        void cannotReplaceItineraryAfterConfirmation() {
            Cargo confirmed = notified().confirm();
            CargoItinerary itinerary = valid();

            assertThatThrownBy(() -> confirmed.assignItinerary(itinerary, LA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("確定");
        }

        @Test
        @DisplayName("追跡番号を発行した予約の経路も差し替えられない")
        void cannotReplaceItineraryAfterIssuing() {
            Cargo issued = notified().confirm()
                    .issueTrackingNumber(TrackingNumber.of("TRK-20260822-0001"));

            CargoItinerary itinerary = valid();

            assertThatThrownBy(() -> issued.assignItinerary(itinerary, LA))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * <strong>差し替えたら、通知の記録は消える</strong>（US12・IT6 タスク 0.7）。
         *
         * <p>残したままだと、営業の画面は「通知しました」と出したまま経路だけが変わる。
         * 営業は変わったことに気づかず、荷主は古い経路の説明を受けたままになる。
         *
         * <p>気づく手段は<strong>手番が営業に戻ること</strong>である。通知の仕組みが無いため、
         * US06・US10 と同じ形（状態で気づかせる）で代替する。
         */
        @Test
        @DisplayName("経路を差し替えると、通知の記録が消えて営業の手番に戻る")
        void replacingItineraryReturnsTheTurnToSales() {
            Cargo replaced = notified().assignItinerary(valid(), LA);

            assertThat(replaced.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
            assertThat(replaced.routeNotification())
                    .as("経路が変わったのに、古い通知の記録が残っている")
                    .isEmpty();
        }

        /**
         * 予約の状態を、要素と<strong>並び順</strong>で固定する。
         *
         * <p>[ADR-021] 決定 5 は「`CANCELLED` は US30 まで足さない」と定めており、
         * <strong>US30（IT9）で足した</strong>。あわせて `IN_TRANSIT` / `DELIVERED` も入る
         * （[ADR-025] 決定 1）。この検査は IT6 から IT9 まで「足していないこと」を守り、
         * 足した日に赤になって<strong>気づかせた</strong>。
         *
         * <p><strong>並び順まで見るのは、荷役の遷移が並び順で「進む向き」を判定している</strong>
         * ためである（{@code Cargo#afterHandling}）。並びを入れ替えると、巻き戻さない守りが
         * 黙って壊れる。
         *
         * <p>`SETTLED` はまだ無い。精算は US23（IT12）であり、
         * {@code BookingStatusTest#hasNoTransitionIntoSettled} が経路の不在を見ている。
         */
        @Test
        @DisplayName("予約の状態は 8 つで、並び順どおりに進む")
        void hasExactlyEightBookingStatusesInOrder() {
            assertThat(BookingStatus.values())
                    .as("状態を足すなら ADR-021 決定 5 と ADR-025 決定 1 を読み直すこと。"
                            + "並びは「進む向き」の判定に使われている")
                    .containsExactly(BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED,
                            BookingStatus.ROUTE_NOTIFIED, BookingStatus.CONFIRMED,
                            BookingStatus.TRACKING_ISSUED, BookingStatus.IN_TRANSIT,
                            BookingStatus.DELIVERED, BookingStatus.CANCELLED);
        }
    }
}
