package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.entities.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 旅程と経路状態（US09 / US11）。
 *
 * <p><strong>旅程の連結制約は行をまたぐため DB の CHECK では守れない。</strong>
 * `Schedule`（IT3）と同じく、集約が守る。
 */
@DisplayName("旅程の割り当て（US09 / US11）")
class CargoItineraryTest {

    private Leg 区間(String voyageNumber, String load, String unload,
            String loadTime, String unloadTime) {
        return Leg.of(voyageNumber, Location.of(load), Location.of(unload),
                Instant.parse(loadTime), Instant.parse(unloadTime));
    }

    private Cargo 仮予約() {
        return Cargo.book(new BookCargoCommand(
                new ShipperId(UUID.randomUUID()),
                new CargoSpecification(CargoType.GENERAL,
                        new Weight(new BigDecimal("1000")), null, null,
                        new Description("電子部品"), null, null),
                // **テストでシステム時計を使わない。** 期限の妥当性はここでの
                // 関心事ではなく、固定日で十分である
                new RouteSpecification(Location.of("JPYOK"), Location.of("DEHAM"),
                        LocalDate.of(2099, Month.DECEMBER, 31))));
    }

    /** 引き渡し済み（経路割り当て待ち）の予約。 */
    private Cargo 引き渡し済みの予約() {
        Cargo cargo = 仮予約();
        cargo.assignToRouting();
        return cargo;
    }

    /**
     * 旅程の無い割り当てを拒否する。
     *
     * <p><strong>黙って何もしない、を作らない。</strong> 経路を割り当てたつもりで
     * 何も起きていない状態は、画面上「未割り当てのまま」としか見えない。
     */
    @Test
    void 旅程が無ければ割り当てられない() {
        assertThatThrownBy(() -> 引き渡し済みの予約().assignItinerary(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("CargoItinerary（旅程）")
    class 旅程 {

        /** 区間が 1 つも無い旅程は成立しない。 */
        @Test
        void 区間の無い旅程を拒否する() {
            assertThatThrownBy(() -> CargoItinerary.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>連結制約。</strong> 区間 n の荷降港 = 区間 n+1 の積込港。
         *
         * <p>つながっていない旅程を認めると、<strong>貨物が途中で行き先を失う</strong>。
         * 行をまたぐため DB の CHECK 制約では守れない。
         */
        @Test
        void つながっていない旅程を拒否する() {
            assertThatThrownBy(() -> CargoItinerary.of(List.of(
                    区間("V001", "JPYOK", "SGSIN",
                            "2026-10-01T10:00:00Z", "2026-10-08T06:00:00Z"),
                    区間("V002", "HKHKG", "DEHAM",
                            "2026-10-09T10:00:00Z", "2026-10-28T06:00:00Z"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("つながっていません");
        }

        /** <strong>時系列制約。</strong> 着く前に次の区間は始まらない。 */
        @Test
        void 前の区間の荷降より前に積み込む旅程を拒否する() {
            assertThatThrownBy(() -> CargoItinerary.of(List.of(
                    区間("V001", "JPYOK", "SGSIN",
                            "2026-10-01T10:00:00Z", "2026-10-08T06:00:00Z"),
                    区間("V002", "SGSIN", "DEHAM",
                            "2026-10-07T10:00:00Z", "2026-10-28T06:00:00Z"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** つながっていれば受け付ける。乗り継ぎ時間 0 も認める。 */
        @Test
        void つながった旅程を受け付ける() {
            var itinerary = CargoItinerary.of(List.of(
                    区間("V001", "JPYOK", "SGSIN",
                            "2026-10-01T10:00:00Z", "2026-10-08T06:00:00Z"),
                    区間("V002", "SGSIN", "DEHAM",
                            "2026-10-08T06:00:00Z", "2026-10-28T06:00:00Z")));

            assertThat(itinerary.legs()).hasSize(2);
        }

        /** 旅程の端点と到着時刻を導く。**保持しない。** */
        @Test
        void 端点と到着時刻を導く() {
            var itinerary = CargoItinerary.of(List.of(
                    区間("V001", "JPYOK", "SGSIN",
                            "2026-10-01T10:00:00Z", "2026-10-08T06:00:00Z"),
                    区間("V002", "SGSIN", "DEHAM",
                            "2026-10-09T10:00:00Z", "2026-10-28T06:00:00Z")));

            assertThat(itinerary.origin()).isEqualTo(Location.of("JPYOK"));
            assertThat(itinerary.destination()).isEqualTo(Location.of("DEHAM"));
            assertThat(itinerary.arrivalTime())
                    .isEqualTo(Instant.parse("2026-10-28T06:00:00Z"));
        }

        /** 区間の積込港と荷降港は異なる。 */
        @Test
        void 同じ港を積込と荷降にする区間を拒否する() {
            assertThatThrownBy(() -> 区間("V001", "JPYOK", "JPYOK",
                    "2026-10-01T10:00:00Z", "2026-10-08T06:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 航海番号の無い区間は成立しない。**どの便で運ぶかが分からない。** */
        @Test
        void 航海番号の無い区間を拒否する() {
            assertThatThrownBy(() -> Leg.of("  ", Location.of("JPYOK"), Location.of("SGSIN"),
                    Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-08T06:00:00Z")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Cargo への割り当て")
    class 割り当て {

        private CargoItinerary 旅程() {
            return CargoItinerary.of(List.of(
                    区間("V001", "JPYOK", "DEHAM",
                            "2026-10-01T10:00:00Z", "2026-10-28T06:00:00Z")));
        }

        /** 受入基準（US11）: 紐付け後、経路状態が「割り当て済」になる。 */
        @Test
        void 旅程を割り当てると経路状態が割り当て済になる() {
            Cargo cargo = 引き渡し済みの予約();

            cargo.assignItinerary(旅程());

            assertThat(cargo.routingStatus()).isEqualTo(CargoRoutingStatus.ROUTED);
            assertThat(cargo.cargoItinerary()).isNotNull();
        }

        /**
         * 受入基準（US11）: <strong>予約状態は「経路提案済」のまま維持される。</strong>
         *
         * <p>経路を確定しても `BookingStatus` は動かない（遷移表 3）。
         */
        @Test
        void 旅程を割り当てても予約状態は変わらない() {
            Cargo cargo = 引き渡し済みの予約();
            var before = cargo.bookingStatus();

            cargo.assignItinerary(旅程());

            assertThat(cargo.bookingStatus()).isEqualTo(before);
        }

        /** 引き渡し前の予約には割り当てられない。**経路設計の対象になっていない。** */
        @Test
        void 引き渡し前の予約には割り当てられない() {
            Cargo preliminary = 仮予約();

            assertThatThrownBy(() -> preliminary.assignItinerary(旅程()))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * <strong>旅程の端点は予約の出発地・目的地と一致する。</strong>
         *
         * <p>一致しない旅程を割り当てると、**荷主が頼んだ場所と違う場所へ運ぶ**ことになる。
         */
        @Test
        void 予約と端点が違う旅程を拒否する() {
            var wrongItinerary = CargoItinerary.of(List.of(
                    区間("V001", "JPOSA", "USLAX",
                            "2026-10-01T10:00:00Z", "2026-10-14T06:00:00Z")));

            assertThatThrownBy(() -> 引き渡し済みの予約().assignItinerary(wrongItinerary))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("出発地");
        }

        /** 新規の予約は「未割り当て」である。 */
        @Test
        void 新規の予約は未割り当てである() {
            assertThat(引き渡し済みの予約().routingStatus())
                    .isEqualTo(CargoRoutingStatus.NOT_ROUTED);
        }
    }

    @Nested
    @DisplayName("CargoRoutingStatus（経路状態）")
    class 経路状態 {

        /** 表示名は `ui_design.md` の付録（正典）に揃える。 */
        @Test
        void 表示名を持つ() {
            assertThat(CargoRoutingStatus.NOT_ROUTED.displayName()).isEqualTo("未割り当て");
            assertThat(CargoRoutingStatus.ROUTED.displayName()).isEqualTo("割り当て済");
            assertThat(CargoRoutingStatus.MISROUTED.displayName()).isEqualTo("誤配");
        }

        /** 3 つである。**Routing の状態とは別の型**だが、値は対応する。 */
        @Test
        void 経路状態は3つである() {
            assertThat(CargoRoutingStatus.values()).hasSize(3);
        }
    }
}
