package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationFeeRate;
import com.example.cargotracker.booking.domain.model.aggregates.CancellationRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationStatus;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 輸送中の予約キャンセルの申請と承認（US30）。
 *
 * <p><strong>輸送中の貨物を、営業担当者の一存で消せなくする。</strong>
 * 貨物は船の上にあり、どこで降ろすかを決めないままキャンセルすると、
 * 荷役の現場は行き先の無い荷物を抱えることになる。
 */
@DisplayName("輸送中の予約キャンセル（US30）")
class CancellationRequestTest {

    private static final Instant 申請時刻 = Instant.parse("2026-08-10T01:00:00Z");
    private static final Instant 決定時刻 = Instant.parse("2026-08-10T02:00:00Z");
    private static final Location 大阪 = new Location("JPOSA");
    private static final Location 東京 = new Location("JPTYO");
    private static final Location 上海 = new Location("CNSHA");

    private static CancellationRequest 申請する() {
        return CancellationRequest.request(
                new BookingId(UUID.randomUUID()), "荷主都合",
                CancellationFeeRate.of(BookingStatus.IN_TRANSIT), "sales1", 申請時刻);
    }

    @Nested
    @DisplayName("料率")
    class 料率 {

        /**
         * <strong>料率は状態で決まる。</strong>
         *
         * <p>何日前に言ったかではなく、<strong>こちらがどこまで手配を進めていたか</strong>が
         * 失う費用を決める。
         */
        @Test
        void 予約の状態で料率が決まる() {
            assertThat(CancellationFeeRate.of(BookingStatus.PRELIMINARY).value())
                    .as("経路も便も押さえていない")
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(CancellationFeeRate.of(BookingStatus.ROUTE_PROPOSED).value())
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(CancellationFeeRate.of(BookingStatus.CONFIRMED).value())
                    .as("便を押さえており、空けた枠は他へ売れない")
                    .isEqualByComparingTo(new BigDecimal("0.20"));
            assertThat(CancellationFeeRate.of(BookingStatus.TRACKING_ISSUED).value())
                    .isEqualByComparingTo(new BigDecimal("0.20"));
            assertThat(CancellationFeeRate.of(BookingStatus.IN_TRANSIT).value())
                    .as("**すでに運んでいる。** 陸揚げと戻しの費用が別途かかる")
                    .isEqualByComparingTo(new BigDecimal("0.50"));
        }

        /**
         * <strong>キャンセルできない状態を 0% にしない。</strong>
         *
         * <p>黙って 0 を返すと、引き渡し済みの貨物に「キャンセル料 0 円」という
         * 意味の通らない答えが出る。
         */
        @Test
        void キャンセルできない状態では料率を決められない() {
            assertThatThrownBy(() -> CancellationFeeRate.of(BookingStatus.DELIVERED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("キャンセルできません");
            assertThatThrownBy(() -> CancellationFeeRate.of(BookingStatus.SETTLED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CancellationFeeRate.of(BookingStatus.CANCELLED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** キャンセル料が発生しない状態がある（<strong>0 円の請求書は作らない</strong>）。 */
        @Test
        void 料率がゼロならキャンセル料は発生しない() {
            assertThat(CancellationFeeRate.of(BookingStatus.PRELIMINARY).chargeable())
                    .isFalse();
            assertThat(CancellationFeeRate.of(BookingStatus.IN_TRANSIT).chargeable())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("申請")
    class 申請 {

        /**
         * <strong>理由を必須にする。</strong>
         *
         * <p>荷主都合か、こちらの遅延か、販売先の倒産かで、
         * 料率の交渉も再発防止も変わる。
         */
        @Test
        void 理由の無い申請は認めない() {
            assertThatThrownBy(() -> CancellationRequest.request(
                    new BookingId(UUID.randomUUID()), "  ",
                    CancellationFeeRate.of(BookingStatus.IN_TRANSIT), "sales1", 申請時刻))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由");
        }

        /** 申請した直後は承認待ちである。 */
        @Test
        void 申請した直後は承認待ちである() {
            CancellationRequest request = 申請する();

            assertThat(request.status()).isEqualTo(CancellationStatus.PENDING);
            assertThat(request.isPending()).isTrue();
            assertThat(request.dischargeLocation())
                    .as("**降ろす場所は承認で決まる**（申請では決まらない）")
                    .isNull();
        }

        /**
         * <strong>申請時点の料率を保持する。</strong>
         *
         * <p>申請してから承認されるまでに輸送が始まれば料率は上がる。
         * <strong>承認が遅れたことの費用を荷主に負わせない。</strong>
         */
        @Test
        void 申請時点の料率を保持する() {
            CancellationRequest request = CancellationRequest.request(
                    new BookingId(UUID.randomUUID()), "荷主都合",
                    CancellationFeeRate.of(BookingStatus.CONFIRMED), "sales1", 申請時刻);

            assertThat(request.feeRate().value())
                    .as("承認時に計算し直さない")
                    .isEqualByComparingTo(new BigDecimal("0.20"));
        }
    }

    @Nested
    @DisplayName("承認と却下")
    class 承認と却下 {

        /**
         * <strong>陸揚げ地は候補の中からしか選べない。</strong>
         *
         * <p>船が寄らない港を指定すると、降ろせない場所で降ろす手配をすることになる。
         */
        @Test
        void 候補にない陸揚げ地は指定できない() {
            CancellationRequest request = 申請する();

            assertThatThrownBy(() -> request.approve(
                    上海, List.of(大阪, 東京), "tracker1", 決定時刻))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("降ろせません");
            assertThat(request.isPending())
                    .as("拒んだ承認で状態を動かさない")
                    .isTrue();
        }

        /** 候補の中から選べば承認できる。 */
        @Test
        void 候補の陸揚げ地を選んで承認できる() {
            CancellationRequest request = 申請する();

            request.approve(大阪, List.of(大阪, 東京), "tracker1", 決定時刻);

            assertThat(request.status()).isEqualTo(CancellationStatus.APPROVED);
            assertThat(request.dischargeLocation()).isEqualTo(大阪);
            assertThat(request.decision().by()).isEqualTo("tracker1");
        }

        /**
         * <strong>申請した本人は承認できない。</strong>
         *
         * <p>一人で申請と承認ができるなら、承認という段階は形だけになる。
         */
        @Test
        void 申請した本人は承認できない() {
            CancellationRequest request = 申請する();

            assertThatThrownBy(() -> request.approve(
                    大阪, List.of(大阪), "sales1", 決定時刻))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("本人は承認できません");
        }

        /**
         * <strong>二度は決められない。</strong>
         *
         * <p>決め直しを許すと、却下されたものを後から承認でき、
         * 決定の日時と決定者が上書きされる。
         */
        @Test
        void 決着した申請は二度決められない() {
            CancellationRequest request = 申請する();
            request.approve(大阪, List.of(大阪), "tracker1", 決定時刻);

            assertThatThrownBy(() -> request.reject("tracker2", 決定時刻, "やはり続ける"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("二度決める");
        }

        /** <strong>却下には理由が要る</strong> — 申請者は次に何をすればよいか分からない。 */
        @Test
        void 理由の無い却下は認めない() {
            CancellationRequest request = 申請する();

            assertThatThrownBy(() -> request.reject("tracker1", 決定時刻, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("却下の理由");
        }

        /** 却下しても陸揚げ地は決まらない（<strong>降ろさないのだから</strong>）。 */
        @Test
        void 却下した申請は陸揚げ地を持たない() {
            CancellationRequest request = 申請する();

            request.reject("tracker1", 決定時刻, "代替の販売先が見つかったため輸送を続ける");

            assertThat(request.status()).isEqualTo(CancellationStatus.REJECTED);
            assertThat(request.dischargeLocation()).isNull();
            assertThat(request.decision().reason()).contains("輸送を続ける");
        }
    }
}
