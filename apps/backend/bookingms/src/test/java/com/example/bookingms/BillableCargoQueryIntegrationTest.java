package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.port.BillableCargoFinder;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.ShipperType;
import java.math.BigDecimal;
import com.example.bookingms.application.port.BillableCargo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 料金算出の対象を引くクエリ（US21・[ADR-027] 決定 5・決定 7）。
 *
 * <p><strong>実 DB で確かめる。</strong>絞り（引取済・キャンセル済み）と並び（引取が
 * 終わった順）と JOIN（荷主・地点・キャンセル申請）は、いずれも DB の振る舞いに依存する。
 * スタブでは<strong>列名の誤りも JOIN の取りこぼしも見つからない</strong>——実際、
 * 方言スモークが 3 つの列名誤りを捕まえた。
 */
@DisplayName("料金算出の対象を引く")
class BillableCargoQueryIntegrationTest extends CargoPersistenceTestBase {

    @Autowired
    private BillableCargoFinder billable;

    /** 契約割引 10% の法人荷主。**採番は本番経路（シーケンス）に任せる**。 */
    private long corporateShipperId;

    /**
     * <strong>自分が入れた行を残さない。</strong>
     *
     * <p>DB は {@link CargoPersistenceTestBase} を継承するテストで共有している。
     * 残すと、一覧を読む他のテストが<strong>原因と無関係に落ちる</strong>。
     */
    @org.junit.jupiter.api.AfterEach
    void removeInsertedCargoes() {
        jdbcTemplate.update("DELETE FROM cancellation_request");
        jdbcTemplate.update("DELETE FROM leg WHERE cargo_id IN"
                + " (SELECT id FROM cargo WHERE booking_id LIKE 'BKG-9%')");
        jdbcTemplate.update("DELETE FROM cargo WHERE booking_id LIKE 'BKG-9%'");
    }

    @BeforeEach
    void clearBillableCargoes() {
        jdbcTemplate.update("DELETE FROM cancellation_request");
        jdbcTemplate.update("DELETE FROM leg");
        jdbcTemplate.update("DELETE FROM cargo");

        // **本番と同じ登録経路を使う。** 自前で採番すると、シーケンスと衝突して
        // 原因でない他のテストが UNIQUE 制約で落ちる
        RegistrationOutcome outcome = registerShipper.register(new RegisterShipperCommand(
                ShipperType.CORPORATE, "丸紅商事株式会社",
                "billable-" + java.util.UUID.randomUUID() + "@example.com",
                "東京都千代田区 1-1-1", "03-1234-5678",
                new CorporateContract(ContractNumber.of("CN-2026-9001"),
                        DiscountRate.ofRate(new BigDecimal("0.1000")))));
        corporateShipperId = ((RegistrationOutcome.Registered) outcome).shipper().id();
    }

    /**
     * <strong>引取が終わっていない予約は対象にしない</strong>（決定 5）。
     *
     * <p>まだ運び終えておらず、請求する金額が決まらない。<strong>絞りはここに置く</strong>
     * ——呼び出し側で絞ると、画面と API で別々の条件を持つことになる。
     */
    @Test
    @DisplayName("引取済とキャンセル済みだけを対象にする")
    void selectsOnlyDeliveredAndCancelledCargoes() {
        long delivered = insertCargo("BKG-9000000001", "DELIVERED",
                Instant.parse("2027-09-26T00:00:00Z"));
        long cancelled = insertCargo("BKG-9000000002", "CANCELLED", null);
        insertCargo("BKG-9000000003", "IN_TRANSIT", null);
        insertCargo("BKG-9000000004", "CONFIRMED", null);
        assertThat(delivered).isPositive();
        assertThat(cancelled).isPositive();

        assertThat(billable.findAllBillable())
                .extracting(BillableCargo::bookingId)
                .as("輸送中や確定済みの予約が対象に混ざっている。運び終える前に請求してしまう")
                .containsExactlyInAnyOrder("BKG-9000000001", "BKG-9000000002");
    }

    /**
     * <strong>引取が終わった順に並べる。</strong>
     *
     * <p>待たせている案件が上に来る——新しい順だと、いちばん待たせている荷主への請求が
     * 下に沈む。
     */
    @Test
    @DisplayName("引取が終わった順に並べ、日時の無いものは後ろに回す")
    void ordersByClaimedAtWithNullsLast() {
        insertCargo("BKG-9000000011", "DELIVERED", Instant.parse("2027-10-05T00:00:00Z"));
        insertCargo("BKG-9000000012", "DELIVERED", Instant.parse("2027-09-20T00:00:00Z"));
        insertCargo("BKG-9000000013", "CANCELLED", null);

        assertThat(billable.findAllBillable())
                .extracting(BillableCargo::bookingId)
                .as("待たせている案件が上に来ていない")
                .containsExactly("BKG-9000000012", "BKG-9000000011", "BKG-9000000013");
    }

    /**
     * <strong>区間数を数える</strong>（決定 1）。
     *
     * <p>距離を持っていないため、これが料金の入力になる。数え損ねると
     * <strong>積み替えのある貨物が直行と同じ運賃になる</strong>。
     */
    @Test
    @DisplayName("旅程の区間数を数える")
    void countsTheLegsOfTheItinerary() {
        long cargoId = insertCargo("BKG-9000000021", "DELIVERED",
                Instant.parse("2027-09-26T00:00:00Z"));
        insertLeg(cargoId, "V0100", "JPTYO", "CNSHA");
        insertLeg(cargoId, "V0200", "CNSHA", "USLAX");

        assertThat(billable.findBillable("BKG-9000000021"))
                .get()
                .extracting(BillableCargo::legCount)
                .as("区間数を数えていない。積み替えのある貨物が直行と同じ運賃になる")
                .isEqualTo(2);
    }

    /** 荷主の社名・種別・割引率を運ぶ（US22）。**率をそのまま運ぶ**（百分率ではない）。 */
    @Test
    @DisplayName("荷主の社名・種別・割引率を運ぶ")
    void carriesTheShipperContract() {
        insertCargo("BKG-9000000031", "DELIVERED", Instant.parse("2027-09-26T00:00:00Z"));

        assertThat(billable.findBillable("BKG-9000000031")).get()
                .satisfies(cargo -> {
                    assertThat(cargo.shipperType()).isEqualTo("CORPORATE");
                    assertThat(cargo.discountRate()).isEqualByComparingTo("0.1000");
                    assertThat(cargo.shipperName()).isNotBlank();
                });
    }

    /**
     * <strong>誤配の記録を、港の名前つきで運ぶ</strong>（US28-8・IT10 レビューの懸念）。
     *
     * <p>載せないと、経理担当者は料金調整の根拠を読む手段を持たない。
     */
    @Test
    @DisplayName("誤配の記録を、港の名前つきで運ぶ")
    void carriesTheMisrouteWithTheLocationName() {
        long cargoId = insertCargo("BKG-9000000041", "DELIVERED",
                Instant.parse("2027-10-02T00:00:00Z"));
        jdbcTemplate.update("""
                UPDATE cargo
                   SET misrouted_at = ?, misrouted_location_unlocode = 'SGSIN'
                 WHERE id = ?
                """, java.sql.Timestamp.from(Instant.parse("2027-09-09T00:00:00Z")), cargoId);

        assertThat(billable.findBillable("BKG-9000000041")).get()
                .extracting(BillableCargo::misroute)
                .isNotNull()
                .satisfies(misroute -> {
                    BillableCargo.Misroute m = (BillableCargo.Misroute) misroute;
                    assertThat(m.locationUnLocode()).isEqualTo("SGSIN");
                    assertThat(m.locationName())
                            .as("港の名前が引けていない。符号だけでは経理担当者が読めない")
                            .isEqualTo("Singapore");
                });
    }

    /** 誤配していなければ、記録は現れない。 */
    @Test
    @DisplayName("誤配していない貨物では、誤配の記録が現れない")
    void omitsTheMisrouteWhenThereIsNone() {
        insertCargo("BKG-9000000051", "DELIVERED", Instant.parse("2027-09-26T00:00:00Z"));

        assertThat(billable.findBillable("BKG-9000000051")).get()
                .extracting(BillableCargo::misroute)
                .isNull();
    }

    /**
     * <strong>承認済みの最新 1 件だけを引く</strong>（US30-9）。
     *
     * <p>却下されて再申請した予約では申請が複数ある。絞らないと<strong>予約が重複して
     * 並ぶ</strong>——経理担当者は同じ貨物に 2 回請求することになる。
     */
    @Test
    @DisplayName("却下と承認が両方あっても、予約は 1 件だけ現れる")
    void takesOnlyTheApprovedCancellationRequest() {
        long cargoId = insertCargo("BKG-9000000061", "CANCELLED", null);
        insertCancellationRequest(cargoId, "REJECTED", "CONFIRMED",
                Instant.parse("2027-09-01T00:00:00Z"));
        insertCancellationRequest(cargoId, "APPROVED", "IN_TRANSIT",
                Instant.parse("2027-09-10T00:00:00Z"));

        List<BillableCargo> all = billable.findAllBillable();

        assertThat(all)
                .as("申請が複数あると、予約が重複して並ぶ。同じ貨物に 2 回請求することになる")
                .hasSize(1);
        assertThat(all.get(0).cancellation()).isNotNull();
        assertThat(all.get(0).cancellation().bookingStatusAtRequest())
                .as("却下された申請の状態を拾っている。料率が変わる")
                .isEqualTo("IN_TRANSIT");
    }

    /** 対象でない予約は空を返す（404 の材料）。 */
    @Test
    @DisplayName("対象でない予約は引けない")
    void doesNotFindCargoThatCannotBeBilled() {
        insertCargo("BKG-9000000071", "IN_TRANSIT", null);

        assertThat(billable.findBillable("BKG-9000000071")).isEmpty();
        assertThat(billable.findBillable("BKG-0000000000")).isEmpty();
    }

    private long insertCargo(String bookingId, String bookingStatus, Instant claimedAt) {
        jdbcTemplate.update("""
                INSERT INTO cargo (booking_id, shipper_id, booking_status, transport_status,
                                   routing_status, cargo_type, weight_kg,
                                   spec_origin_unlocode, spec_destination_unlocode,
                                   spec_arrival_deadline, last_handling_at)
                -- **transport_status は bookingms が知っている値だけを使う。**
                -- この列は同じ DB を共有する他のテストも読む。知らない値を入れると、
                -- 復元の時点で例外になり、**原因と無関係なテストが落ちる**（実際に落とした）
                VALUES (?, ?, ?, 'NOT_RECEIVED', 'ROUTED', 'GENERAL', 4200,
                        'JPTYO', 'USLAX', DATE '2027-10-20', ?)
                """, bookingId, corporateShipperId, bookingStatus,
                claimedAt == null ? null : java.sql.Timestamp.from(claimedAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
    }

    private void insertLeg(long cargoId, String voyageNumber, String from, String to) {
        jdbcTemplate.update("""
                INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode,
                                 unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, ?, ?, ?, TIMESTAMP WITH TIME ZONE '2027-09-02T00:00:00Z',
                        TIMESTAMP WITH TIME ZONE '2027-09-08T00:00:00Z',
                        (SELECT COALESCE(MAX(seq_number), 0) + 1 FROM leg WHERE cargo_id = ?))
                """, cargoId, voyageNumber, from, to, cargoId);
    }

    private void insertCancellationRequest(long cargoId, String status, String statusAtRequest,
            Instant requestedAt) {
        jdbcTemplate.update("""
                INSERT INTO cancellation_request (cargo_id, reason, status, requested_by,
                                                  requested_at, booking_status_at_request)
                VALUES (?, '荷主都合', ?, 'sales01', ?, ?)
                """, cargoId, status, java.sql.Timestamp.from(requestedAt), statusAtRequest);
    }
}
