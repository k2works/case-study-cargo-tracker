package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * キャンセル申請（US30・UC22）。
 *
 * <p>ここで守るのは<strong>承認を迂回できないこと</strong>と、
 * <strong>却下しても予約が止まらないこと</strong>である。
 */
@DisplayName("キャンセル申請")
class CancellationRequestTest {

    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-06T00:00:00Z");

    private static CancellationRequest inTransit() {
        return CancellationRequest.request(1L, "荷主都合", "sales01", AT,
                BookingStatus.IN_TRANSIT, true);
    }

    @Nested
    @DisplayName("申請するとき（US30-1・US30-2）")
    class WhenRequesting {

        /** 輸送中は承認を待つ。貨物が船の上にあり、どこで降ろすかを決める必要がある。 */
        @Test
        @DisplayName("輸送中の申請は、承認待ちになる")
        void awaitsApprovalWhileInTransit() {
            assertThat(inTransit().status()).isEqualTo(CancellationStatus.REQUESTED);
            assertThat(inTransit().awaitingDecision()).isTrue();
            assertThat(inTransit().dischargeLocation()).isEmpty();
        }

        /** <strong>輸送開始前は承認を待つ理由が無い。</strong>貨物はまだ動いていない。 */
        @Test
        @DisplayName("輸送開始前の申請は、その場で確定する")
        void settlesImmediatelyBeforeDeparture() {
            CancellationRequest request = CancellationRequest.request(1L, "荷主都合", "sales01",
                    AT, BookingStatus.CONFIRMED, false);

            assertThat(request.status()).isEqualTo(CancellationStatus.APPROVED);
            assertThat(request.awaitingDecision()).isFalse();
            assertThat(request.decisionReason()).isPresent();
        }

        /** 理由は承認する追跡管理者が読む。「荷主都合」だけでも、無いよりは判断できる。 */
        @Test
        @DisplayName("理由なしの申請は断る")
        void requiresAReason() {
            assertThatThrownBy(() -> CancellationRequest.request(1L, " ", "sales01", AT,
                    BookingStatus.IN_TRANSIT, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由");
        }

        /**
         * <strong>申請時点の予約状態を持つ。</strong>
         *
         * <p>キャンセル料の料率はこれで決まる（US23・IT11）。あとから予約の状態を見ても、
         * そのときどこまで進んでいたかは分からない。
         */
        @Test
        @DisplayName("申請時点の予約状態が残る")
        void remembersTheBookingStatusAtRequest() {
            assertThat(inTransit().bookingStatusAtRequest()).isEqualTo(BookingStatus.IN_TRANSIT);
        }
    }

    @Nested
    @DisplayName("承認するとき（US30-5）")
    class WhenApproving {

        /** <strong>陸揚げ地は必須。</strong>決めないと、承認しても現場は動けない。 */
        @Test
        @DisplayName("陸揚げ地なしの承認は断る")
        void requiresADischargeLocation() {
            CancellationRequest request = inTransit();

            assertThatThrownBy(() ->
                    request.approve(" ", "tracker01", "荷主と合意", DECIDED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("陸揚げ地");
        }

        @Test
        @DisplayName("承認すると、陸揚げ地と決定者が残る")
        void keepsTheDecision() {
            CancellationRequest approved =
                    inTransit().approve("CNSHA", "tracker01", "荷主と合意", DECIDED_AT);

            assertThat(approved.status()).isEqualTo(CancellationStatus.APPROVED);
            assertThat(approved.dischargeLocation()).contains("CNSHA");
            assertThat(approved.decidedBy()).contains("tracker01");
            assertThat(approved.decidedAt()).contains(DECIDED_AT);
        }

        /** 二重の判断は断る。**すでに決まったものを覆さない**。 */
        @Test
        @DisplayName("すでに決まった申請は、もう一度決められない")
        void rejectsDecidingTwice() {
            CancellationRequest approved =
                    inTransit().approve("CNSHA", "tracker01", "荷主と合意", DECIDED_AT);

            assertThatThrownBy(() ->
                    approved.approve("USLAX", "tracker02", "やり直し", DECIDED_AT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> approved.reject("tracker02", "やり直し", DECIDED_AT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("却下するとき（US30-7）")
    class WhenRejecting {

        /** 理由は申請した営業担当者が読む。次にどうするかが分かるように書いてもらう。 */
        @Test
        @DisplayName("理由なしの却下は断る")
        void requiresADecisionReason() {
            CancellationRequest request = inTransit();

            assertThatThrownBy(() -> request.reject("tracker01", " ", DECIDED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由");
        }

        /**
         * <strong>却下しても陸揚げ地は付かない。</strong>
         *
         * <p>却下は「キャンセルしない」という決定である。陸揚げ地が付くと、
         * 荷降しの手配が要ると読まれる。
         */
        @Test
        @DisplayName("却下した申請に陸揚げ地は付かない")
        void doesNotAttachADischargeLocationOnRejection() {
            CancellationRequest rejected =
                    inTransit().reject("tracker01", "積み替え済みのため", DECIDED_AT);

            assertThat(rejected.status()).isEqualTo(CancellationStatus.REJECTED);
            assertThat(rejected.dischargeLocation()).isEmpty();
            assertThat(rejected.decisionReason()).contains("積み替え済みのため");
        }
    }
}
