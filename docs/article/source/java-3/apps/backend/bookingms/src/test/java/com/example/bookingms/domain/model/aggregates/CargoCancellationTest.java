package com.example.bookingms.domain.model.aggregates;

import static com.example.bookingms.domain.model.aggregates.CargoFixtures.ROUTE;
import static com.example.bookingms.domain.model.aggregates.CargoFixtures.specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;

/**
 * 予約のキャンセルと、荷役に応じた状態の進み（US30・[ADR-025] 決定 1）。
 *
 * <p><strong>輸送中かどうかが、キャンセルに承認が要るかを決める。</strong>
 * 貨物が船の上にあると、どこで降ろすかを決めないとキャンセルできない。
 */
@DisplayName("予約のキャンセル")
class CargoCancellationTest {

    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");
    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");

    private static CargoItinerary itinerary() {
        // 期限は ROUTE の 2026-09-20。**期限内に着く旅程でないと割り当てを断られる**
        return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"), TOKYO, LOS_ANGELES,
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-15T12:00:00Z"))));
    }

    /** 追跡番号まで発行した予約。ここから荷役が動き出す。 */
    private static Cargo tracked() {
        return Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE)
                .requestRouting()
                .assignItinerary(itinerary(), LA)
                .notifyShipper(Instant.parse("2026-08-22T02:00:00Z"), "sales01")
                .confirm()
                .issueTrackingNumber(TrackingNumber.of("TRK-20260823-0001"));
    }

    @Nested
    @DisplayName("荷役に応じて進むとき（[ADR-025] 決定 1）")
    class WhenHandlingHappens {

        /**
         * <strong>最初の積込で輸送中になる。</strong>
         *
         * <p>bookingms は自分では知らない——荷役の記録が一次情報である。
         * 受領（RECEIVE）ではまだ港にあり、船に載っていない。
         */
        @Test
        @DisplayName("積込で輸送中になる")
        void becomesInTransitOnLoad() {
            assertThat(tracked().afterHandling("RECEIVE", "JPTYO", AT).bookingStatus())
                    .as("受領で輸送中にしている。まだ港にある")
                    .isEqualTo(BookingStatus.TRACKING_ISSUED);

            assertThat(tracked().afterHandling("LOAD", "JPTYO", AT).bookingStatus())
                    .isEqualTo(BookingStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("引取で配送完了になる")
        void becomesDeliveredOnClaim() {
            assertThat(tracked().afterHandling("LOAD", "JPTYO", AT)
                    .afterHandling("CLAIM", "USLAX", AT).bookingStatus())
                    .isEqualTo(BookingStatus.DELIVERED);
        }

        /**
         * <strong>巻き戻さない。</strong>
         *
         * <p>再試行やデッドレターからの送り直しで、荷役の届く順は入れ替わる。順序を
         * 信じて上書きすると、あとから届いた古い作業で予約が輸送中へ戻り、荷主は
         * 「配送完了だったはずの貨物が輸送中に戻っている」を見る。
         */
        @Test
        @DisplayName("あとから古い荷役が届いても、状態は巻き戻らない")
        void neverRegresses() {
            Cargo delivered = tracked().afterHandling("LOAD", "JPTYO", AT)
                    .afterHandling("CLAIM", "USLAX", AT);

            assertThat(delivered.afterHandling("LOAD", "JPTYO", AT).bookingStatus())
                    .as("古い積込で配送完了から巻き戻っている")
                    .isEqualTo(BookingStatus.DELIVERED);
        }

        /** <strong>冪等である。</strong>同じ荷役が 2 回届いても 1 度しか動かない。 */
        @Test
        @DisplayName("同じ荷役が 2 回届いても、結果は変わらない")
        void isIdempotent() {
            Cargo once = tracked().afterHandling("LOAD", "JPTYO", AT);

            assertThat(once.afterHandling("LOAD", "JPTYO", AT).bookingStatus())
                    .isEqualTo(once.bookingStatus());
        }

        /**
         * <strong>最後の荷役地点を持つ</strong>（[ADR-025] 決定 4）。
         *
         * <p>陸揚げ地の候補「現在地の港」はこれを使う。trackingms へ引かない——
         * 現在地の一次情報は荷役にあり、2 ホップ先から取りに行く形にしない。
         */
        @Test
        @DisplayName("最後の荷役地点と日時を持つ")
        void remembersWhereItWasHandledLast() {
            Cargo loaded = tracked().afterHandling("LOAD", "JPTYO", AT);

            assertThat(loaded.lastHandlingLocation()).contains("JPTYO");
            assertThat(loaded.lastHandlingAt()).contains(AT);
        }

        /**
         * キャンセル済みの予約は、遅れて届いた荷役では<strong>何も動かない</strong>。
         *
         * <p>状態だけを見ると、列挙の並び順が偶然守ってしまう（キャンセルは配送完了より
         * あとにある）。<strong>並びを変えた日に壊れる守り</strong>を、検査が「効いている」
         * と誤認しないよう、<strong>最後の荷役地点まで</strong>動かないことを見る。
         *
         * <p>実際、この検査を状態だけで書いていたとき、ガードを外しても緑のままだった。
         */
        @Test
        @DisplayName("キャンセル済みの予約は、荷役では状態も地点も動かない")
        void ignoresHandlingAfterCancellation() {
            Cargo cancelled = tracked().cancel();

            Cargo afterLateHandling = cancelled.afterHandling("LOAD", "JPTYO", AT);

            assertThat(afterLateHandling.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(afterLateHandling.lastHandlingLocation())
                    .as("キャンセル済みなのに、遅れて届いた荷役で現在地が動いている")
                    .isEmpty();
            assertThat(afterLateHandling.lastHandlingAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("キャンセルするとき（US30）")
    class WhenCancelling {

        /** 輸送開始前は、承認を待たずに確定する（US30-2）。 */
        @Test
        @DisplayName("輸送開始前はキャンセルが確定する")
        void cancelsImmediatelyBeforeDeparture() {
            assertThat(tracked().cancel().bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        /** <strong>輸送中かどうかを、集約が答える。</strong>画面が状態名を見比べない。 */
        @Test
        @DisplayName("輸送中かどうかを答える")
        void tellsWhetherItIsInTransit() {
            assertThat(tracked().isInTransit()).isFalse();
            assertThat(tracked().afterHandling("LOAD", "JPTYO", AT).isInTransit()).isTrue();
        }

        /** 配送完了した貨物はキャンセルできない。すでに荷受人へ引き渡している。 */
        @Test
        @DisplayName("配送完了した予約はキャンセルできない")
        void rejectsCancellingAfterDelivery() {
            Cargo delivered = tracked().afterHandling("LOAD", "JPTYO", AT)
                    .afterHandling("CLAIM", "USLAX", AT);

            assertThat(delivered.canRequestCancellation()).isFalse();
            assertThatThrownBy(delivered::cancel).isInstanceOf(IllegalStateException.class);
        }

        /** 二重のキャンセルは断る。 */
        @Test
        @DisplayName("すでにキャンセルされた予約はキャンセルできない")
        void rejectsCancellingTwice() {
            Cargo cancelled = tracked().cancel();

            assertThat(cancelled.canRequestCancellation()).isFalse();
            assertThatThrownBy(cancelled::cancel).isInstanceOf(IllegalStateException.class);
        }
    }
}
