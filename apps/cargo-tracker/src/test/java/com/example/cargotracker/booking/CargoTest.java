package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.BookingCommandType;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** {@code Cargo} 集約の不変条件を検証する。 */
// テストは @Nested の内側にある。SonarQube は親クラスの @Test だけを数えるため
// 「テストが無い」と判定するが、実際には親クラス経由ですべて実行される。
// **入れ子をやめると、値オブジェクトごとの区切りが失われる。** 構造を優先する。
@SuppressWarnings("java:S2187")
class CargoTest {

    private static final LocalDate TODAY = LocalDate.of(2026, java.time.Month.AUGUST, 6);
    private static final ShipperId SHIPPER = ShipperId.generate();

    private static RouteSpecification 大阪からロサンゼルス() {
        return RouteSpecification.of(
                Location.of("JPOSA"), Location.of("USLAX"), TODAY.plusDays(30), TODAY);
    }

    private static CargoSpecification 貨物仕様() {
        return new CargoSpecification(
                CargoType.GENERAL,
                Weight.ofKilograms(new BigDecimal("1200.5")),
                Dimensions.ofCentimeters(
                        new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100")),
                Quantity.of(10),
                Description.of("電子部品"));
    }

    private static CargoSpecification 必須のみの貨物仕様() {
        return CargoSpecification.of(CargoType.GENERAL, Weight.ofKilograms(BigDecimal.ONE));
    }

    private static BookCargoCommand 予約コマンド() {
        return new BookCargoCommand(SHIPPER, 貨物仕様(), 大阪からロサンゼルス());
    }

    private static Cargo 状態がの予約(BookingStatus status) {
        return Cargo.reconstruct(
                Cargo.book(予約コマンド()).bookingId(),
                SHIPPER,
                必須のみの貨物仕様(),
                大阪からロサンゼルス(),
                status,
                3L);
    }

    @Nested
    @DisplayName("予約の登録")
    class 登録 {

        @Test
        void 登録すると仮受付になり予約番号が発行される() {
            Cargo cargo = Cargo.book(予約コマンド());

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
            assertThat(cargo.bookingId()).isNotNull();
            assertThat(cargo.shipperId()).isEqualTo(SHIPPER);
        }

        @Test
        void 予約番号は予約ごとに異なる() {
            assertThat(Cargo.book(予約コマンド()).bookingId())
                    .isNotEqualTo(Cargo.book(予約コマンド()).bookingId());
        }

        @Test
        void 荷主が指定されていない予約を拒否する() {
            BookCargoCommand command =
                    new BookCargoCommand(null, 必須のみの貨物仕様(), 大阪からロサンゼルス());

            assertThatThrownBy(() -> Cargo.book(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷主");
        }

        @Test
        void 貨物種別が指定されていない貨物仕様を拒否する() {
            assertThatThrownBy(() ->
                    CargoSpecification.of(null, Weight.ofKilograms(BigDecimal.ONE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
        }

        @Test
        void ルート仕様が指定されていない予約を拒否する() {
            BookCargoCommand command = new BookCargoCommand(SHIPPER, 必須のみの貨物仕様(), null);

            assertThatThrownBy(() -> Cargo.book(command))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 寸法・個数・品名はオプション（domain-model.md）。無くても予約は成立する。 */
        @Test
        void 寸法と個数と品名は無くても登録できる() {
            Cargo cargo = Cargo.book(
                    new BookCargoCommand(SHIPPER, 必須のみの貨物仕様(), 大阪からロサンゼルス()));

            assertThat(cargo.cargoSpecification().dimensions()).isNull();
            assertThat(cargo.cargoSpecification().quantity()).isNull();
            assertThat(cargo.cargoSpecification().description()).isNull();
        }
    }

    /**
     * 日本時間の未明でも、その日を到着期限にした予約を受け付ける。
     *
     * <p><strong>時計が UTC のままだと、日本時間の 0 時から 9 時のあいだ
     * 当日着の予約が拒否される。</strong> 朝いちばんに当日着を登録しようとして
     * 弾かれると業務が止まる。日中しか動かさなければ気づかない欠陥である。
     *
     * <p>業務日付の決まり方そのものは {@code BusinessClockTest} が検証する。
     */
    @Test
    void 業務上の当日を到着期限にした予約を受け付ける() {
        // 2026-08-06 15:11 UTC = 2026-08-07 00:11 JST
        java.time.Clock clock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-08-06T15:11:00Z"),
                java.time.ZoneId.of("Asia/Tokyo"));
        LocalDate today = LocalDate.now(clock);

        assertThat(today).isEqualTo(LocalDate.of(2026, java.time.Month.AUGUST, 7));
        org.assertj.core.api.Assertions.assertThatCode(() -> RouteSpecification.of(
                Location.of("JPOSA"), Location.of("USLAX"), today, today))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("キャンセル")
    class キャンセル {

        @Test
        void 仮受付の予約はキャンセルできる() {
            Cargo cargo = Cargo.book(予約コマンド());

            assertThat(cargo.canCancel()).isTrue();
            cargo.cancel();

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        void キャンセル済みの予約はもうキャンセルできない() {
            Cargo cargo = Cargo.book(予約コマンド());
            cargo.cancel();

            assertThat(cargo.canCancel()).isFalse();
            assertThatThrownBy(cargo::cancel)
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        /**
         * 画面のボタン出し分けは集約の述語をそのまま使う。
         *
         * <p>**「押せるのに実行すると失敗する」ボタンを作らないための固定である。**
         */
        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void キャンセル可否の判定が遷移表と一致する(BookingStatus status) {
            assertThat(状態がの予約(status).canCancel())
                    .isEqualTo(status.canTransitionBy(BookingCommandType.CANCEL_BOOKING));
        }
    }

    @Nested
    @DisplayName("経路設計者への引き渡し（US06）")
    class 引き渡し {

        @Test
        void 仮予約の予約は引き渡せる() {
            Cargo cargo = Cargo.book(予約コマンド());

            assertThat(cargo.canAssignToRouting()).isTrue();
            cargo.assignToRouting();

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        }

        /** 受入基準: 引き渡し済みの予約は重ねて引き渡せない。 */
        @Test
        void 引き渡し済みの予約は重ねて引き渡せない() {
            Cargo cargo = Cargo.book(予約コマンド());
            cargo.assignToRouting();

            assertThat(cargo.canAssignToRouting()).isFalse();
            assertThatThrownBy(cargo::assignToRouting)
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        /**
         * 画面のボタン出し分けは集約の述語をそのまま使う。
         *
         * <p>**引き渡し済みの予約に「引き渡す」ボタンが出ていると、二重に依頼が飛ぶ。**
         */
        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void 引き渡し可否の判定が遷移表と一致する(BookingStatus status) {
            assertThat(状態がの予約(status).canAssignToRouting())
                    .isEqualTo(status.canTransitionBy(BookingCommandType.ASSIGN_TO_ROUTING));
        }
    }

    @Nested
    @DisplayName("再構築（永続化からの復元）")
    class 再構築 {

        /**
         * 状態は履歴から導出せず、保存された値をそのまま復元する。
         *
         * <p>**導出すると、ユニットテストは緑のままでも別リクエストで状態が巻き戻る。**
         */
        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void 保存された状態をそのまま復元する(BookingStatus status) {
            Cargo cargo = 状態がの予約(status);

            assertThat(cargo.bookingStatus()).isEqualTo(status);
            assertThat(cargo.version()).isEqualTo(3L);
        }
    }
}
