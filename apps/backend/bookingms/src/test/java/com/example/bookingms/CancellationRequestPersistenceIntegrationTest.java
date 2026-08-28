package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.repository.CancellationRequestRepository;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.aggregates.CancellationRequest;
import com.example.bookingms.domain.model.valueobjects.CancellationStatus;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * キャンセル申請が実際の DB で成立することを確認する（US30）。
 *
 * <p><strong>保存して読み直してから検証する。</strong>手元の集約を見ても、行に
 * 残っていないことに気づけない。
 */
@DisplayName("キャンセル申請の永続化")
class CancellationRequestPersistenceIntegrationTest extends CargoPersistenceTestBase {

    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-06T00:00:00Z");

    @Autowired
    private CancellationRequestRepository cancellations;

    private Long cargoId(String email) {
        Cargo booked = bookCargo.book(command(shipperId("キャンセル太郎", email),
                CargoType.GENERAL));
        return booked.id();
    }

    private CancellationRequest requestFor(Long cargoId) {
        return cancellations.save(CancellationRequest.request(cargoId, "荷主都合", "sales01", AT,
                BookingStatus.IN_TRANSIT, true));
    }

    @Test
    @DisplayName("申請した内容が、読み直しても全項目そろっている")
    void keepsEveryFieldAcrossAReload() {
        CancellationRequest saved = requestFor(cargoId("cancel-reload@example.com"));

        CancellationRequest reloaded =
                cancellations.findLatestByCargoId(saved.cargoId()).orElseThrow();

        // 項目ごとに比べる形にすると、属性が増えたときに比較を足し忘れる
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
    }

    /** **申請時点の予約状態が行に残る。**キャンセル料の料率の根拠になる（US23・IT11）。 */
    @Test
    @DisplayName("申請時点の予約状態が行に残る")
    void persistsTheBookingStatusAtRequest() {
        CancellationRequest saved = requestFor(cargoId("cancel-status@example.com"));

        assertThat(cancellations.findLatestByCargoId(saved.cargoId()).orElseThrow()
                .bookingStatusAtRequest())
                .isEqualTo(BookingStatus.IN_TRANSIT);
    }

    /** 承認すると陸揚げ地が行に残る。**荷役の担当者はここを見る**（[ADR-025] 決定 5）。 */
    @Test
    @DisplayName("承認した陸揚げ地が行に残る")
    void persistsTheDischargeLocation() {
        CancellationRequest saved = requestFor(cargoId("cancel-approve@example.com"));

        cancellations.updateDecision(
                saved.approve("CNSHA", "tracker01", "荷主と合意", DECIDED_AT));

        CancellationRequest reloaded =
                cancellations.findLatestByCargoId(saved.cargoId()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(CancellationStatus.APPROVED);
        assertThat(reloaded.dischargeLocation()).contains("CNSHA");
        assertThat(reloaded.decidedBy()).contains("tracker01");
    }

    /**
     * <strong>承認しても行は増えない。</strong>
     *
     * <p>「常に INSERT する save」で更新まで賄うと、最初の承認のときに行が増える。
     */
    @Test
    @DisplayName("承認しても、申請の行は増えない")
    void doesNotInsertARowOnDecision() {
        CancellationRequest saved = requestFor(cargoId("cancel-noinsert@example.com"));

        cancellations.updateDecision(
                saved.approve("CNSHA", "tracker01", "荷主と合意", DECIDED_AT));

        assertThat(cancellations.findAwaitingByCargoId(saved.cargoId()))
                .as("承認したのに、判断待ちの申請が残っている")
                .isEmpty();
    }

    /** US30-4。承認待ちの一覧は**古い順**——放っておくほど貨物は目的地へ近づく。 */
    @Test
    @DisplayName("承認待ちの一覧は、古い順に並ぶ")
    void listsAwaitingDecisionOldestFirst() {
        Long older = cargoId("cancel-older@example.com");
        Long newer = cargoId("cancel-newer@example.com");
        cancellations.save(CancellationRequest.request(older, "先の申請", "sales01",
                Instant.parse("2026-09-01T00:00:00Z"), BookingStatus.IN_TRANSIT, true));
        cancellations.save(CancellationRequest.request(newer, "後の申請", "sales01",
                Instant.parse("2026-09-09T00:00:00Z"), BookingStatus.IN_TRANSIT, true));

        assertThat(cancellations.findAwaitingDecision(100))
                .extracting(CancellationRequest::cargoId)
                .containsSubsequence(older, newer);
    }
}
