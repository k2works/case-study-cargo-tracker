package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.ClaimCode;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCommandType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoProgress;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoRouting;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.InvalidBookingStatusTransitionException;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
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
                Description.of("電子部品"),
                null, null);
    }

    private static CargoSpecification 必須のみの貨物仕様() {
        return CargoSpecification.of(CargoType.GENERAL, Weight.ofKilograms(BigDecimal.ONE));
    }

    private static BookCargoCommand 予約コマンド() {
        return new BookCargoCommand(SHIPPER, 貨物仕様(), 大阪からロサンゼルス());
    }

    private static com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary 大阪発の旅程() {
        return com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary.of(java.util.List.of(
                com.example.cargotracker.booking.domain.model.entities.Leg.of(
                        "V001", Location.of("JPOSA"), Location.of("USLAX"),
                        java.time.Instant.parse("2026-09-01T10:00:00Z"),
                        java.time.Instant.parse("2026-09-20T06:00:00Z"))));
    }

    /** 経路が割り当て済みの予約。<strong>確定はここからしか始まらない</strong>（遷移表 #4）。 */
    private static Cargo 経路割り当て済みの予約(BookingStatus status) {
        return Cargo.reconstruct(
                Cargo.book(予約コマンド()).bookingId(),
                SHIPPER,
                必須のみの貨物仕様(),
                大阪からロサンゼルス(),
                new CargoProgress(status, CargoRouting.routed(大阪発の旅程()), null),
                3L);
    }

    private static Cargo 状態がの予約(BookingStatus status) {
        return Cargo.reconstruct(
                Cargo.book(予約コマンド()).bookingId(),
                SHIPPER,
                必須のみの貨物仕様(),
                大阪からロサンゼルス(),
                new CargoProgress(status, CargoRouting.notRouted(), null),
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

            assertThat(cargo.bookingStatus().canCancelImmediately()).isTrue();
            cargo.cancel();

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        void キャンセル済みの予約はもうキャンセルできない() {
            Cargo cargo = Cargo.book(予約コマンド());
            cargo.cancel();

            assertThat(cargo.bookingStatus().canCancelImmediately()).isFalse();
            assertThatThrownBy(cargo::cancel)
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        /**
         * <strong>輸送中は営業担当者の操作でキャンセルできない</strong>（US30）。
         *
         * <p>貨物は船の上にある。<strong>どこで降ろすかを決めないままキャンセルすると
         * 貨物が宙に浮き、荷役の現場は行き先の無い荷物を抱える。</strong>
         *
         * <p>「押せるのに実行すると失敗する」ボタンを作らないための固定でもある。
         */
        @Test
        void 輸送中は即座にキャンセルできず承認が要る() {
            Cargo cargo = 状態がの予約(BookingStatus.IN_TRANSIT);

            assertThat(cargo.bookingStatus().canCancelImmediately())
                    .as("**営業担当者には申請ボタンのみを見せる**")
                    .isFalse();
            assertThat(cargo.bookingStatus().requiresCancelApproval()).isTrue();
            assertThatThrownBy(cargo::cancel)
                    .as("画面を通らない経路でも止める")
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);

            cargo.approveCancel();
            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        /**
         * <strong>承認は輸送中にしか効かない</strong>（US30）。
         *
         * <p>申請してから承認までに引取が済むことがある。
         * <strong>引き渡し済みの貨物をキャンセルすると返送の業務になる</strong>。
         */
        @ParameterizedTest
        @EnumSource(value = BookingStatus.class,
                names = {"PRELIMINARY", "ROUTE_PROPOSED", "CONFIRMED", "TRACKING_ISSUED",
                    "DELIVERED", "SETTLED", "CANCELLED"})
        void 輸送中以外は承認しても状態が動かない(BookingStatus status) {
            Cargo cargo = 状態がの予約(status);

            assertThat(status.requiresCancelApproval()).isFalse();
            assertThatThrownBy(cargo::approveCancel)
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
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
    @DisplayName("予約の確定（US13）")
    class 確定 {

        /** 受入基準: 確定操作を行うと予約状態が「予約確定」に更新される。 */
        @Test
        void 経路が割り当てられた予約は確定できる() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.ROUTE_PROPOSED);

            assertThat(cargo.canConfirm()).isTrue();
            cargo.confirm(ClaimCode.of("CLM-1A2B3C4D"));

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        /**
         * <strong>経路が割り当てられていない予約は確定できない</strong>（遷移表 #4 の事前条件）。
         *
         * <p>確定できてしまうと、<strong>運ぶ道筋の無い予約に荷主の同意が付く</strong>。
         * この条件は状態だけでは判定できないため、集約が守る。
         */
        @Test
        void 経路が割り当てられていない予約は確定できない() {
            Cargo cargo = 状態がの予約(BookingStatus.ROUTE_PROPOSED);

            assertThat(cargo.canConfirm()).isFalse();
            assertThatThrownBy(() -> cargo.confirm(ClaimCode.of("CLM-1A2B3C4D")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("経路");
        }

        /** 確定済みの予約はもう確定できない。**二度押しても状態が進まない。** */
        @Test
        void 確定済みの予約はもう確定できない() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.CONFIRMED);

            assertThat(cargo.canConfirm()).isFalse();
            assertThatThrownBy(() -> cargo.confirm(ClaimCode.of("CLM-1A2B3C4D")))
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        /**
         * 画面のボタン出し分けは集約の述語をそのまま使う。
         *
         * <p><strong>経路が割り当てられている場合に限り</strong>遷移表と一致する。
         */
        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void 確定可否の判定が遷移表と一致する(BookingStatus status) {
            assertThat(経路割り当て済みの予約(status).canConfirm())
                    .isEqualTo(status.canTransitionBy(BookingCommandType.CONFIRM_BOOKING));
        }
    }

    @Nested
    @DisplayName("追跡番号の発行（US14）")
    class 追跡番号 {

        /** 受入基準: 「予約確定」状態の予約に対して追跡番号を発行できる。 */
        @Test
        void 確定した予約に追跡番号を発行できる() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.CONFIRMED);

            assertThat(cargo.canIssueTrackingNumber()).isTrue();
            cargo.issueTrackingNumber(
                    new com.example.cargotracker.booking.domain.model.valueobjects.BookingTrackingNumber(
                            "TRK-20260901-0001"));

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
            assertThat(cargo.trackingNumber().value()).isEqualTo("TRK-20260901-0001");
        }

        /** 確定していない予約には発行できない。 */
        @Test
        void 確定していない予約には発行できない() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.ROUTE_PROPOSED);

            assertThat(cargo.canIssueTrackingNumber()).isFalse();
            assertThatThrownBy(() -> cargo.issueTrackingNumber(
                    new com.example.cargotracker.booking.domain.model.valueobjects.BookingTrackingNumber(
                            "TRK-20260901-0002")))
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        /** 発行前の予約は追跡番号を持たない。**空文字で「持っている」ことにしない。** */
        @Test
        void 発行前の予約は追跡番号を持たない() {
            assertThat(経路割り当て済みの予約(BookingStatus.CONFIRMED).trackingNumber()).isNull();
        }

        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void 発行可否の判定が遷移表と一致する(BookingStatus status) {
            assertThat(経路割り当て済みの予約(status).canIssueTrackingNumber())
                    .isEqualTo(status.canTransitionBy(
                            BookingCommandType.ASSIGN_TRACKING_NUMBER));
        }
    }

    @Nested
    @DisplayName("輸送の開始（US15。最初の積込による自動遷移）")
    class 輸送開始 {

        /** 受入基準（遷移表 #6）: 最初の積込で輸送中になる。 */
        @Test
        void 追跡番号発行済の予約は最初の積込で輸送中になる() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.TRACKING_ISSUED);

            assertThat(cargo.canStartTransport()).isTrue();
            cargo.startTransport();

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.IN_TRANSIT);
        }

        /**
         * <strong>すでに輸送中の貨物に積込を記録しても状態は動かない。</strong>
         *
         * <p>積込は輸送中に何度も起きる（積み替え）。そのたびに遷移を試みて例外に
         * なると、<strong>正しい荷役の記録が拒否される</strong>。遷移するのは
         * 最初の 1 回だけであり、それ以外は「進める必要が無い」だけである。
         */
        @Test
        void 輸送中の貨物は輸送を開始できない() {
            Cargo cargo = 経路割り当て済みの予約(BookingStatus.IN_TRANSIT);

            assertThat(cargo.canStartTransport()).isFalse();
            assertThatThrownBy(cargo::startTransport)
                    .isInstanceOf(InvalidBookingStatusTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void 輸送開始可否の判定が遷移表と一致する(BookingStatus status) {
            assertThat(経路割り当て済みの予約(status).canStartTransport())
                    .isEqualTo(status.canTransitionBy(BookingCommandType.START_TRANSPORT));
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
