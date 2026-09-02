package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.internal.commandservices.RegistrationOutcome;
import com.example.bookingms.domain.repository.BillableCargoFinder;
import com.example.bookingms.application.internal.queryservices.SearchShipperUseCase;
import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.repository.BillableCargo;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.commands.RegisterShipperCommand;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.ShipperType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * シミュレーション由来を、実データの一覧から外す（[ADR-030] 決定 3）。
 *
 * <p><strong>検査は除外する側に置く。</strong>作る側だけを確かめても、
 * 「作れている」ことしか分からない——混ざっていないことは、混ざる場所で見るしかない。
 *
 * <p>混ざったときに起きるのは、経理担当者の締めにシミュレーションの請求書が乗り、
 * 荷主一覧に実在しない会社が並ぶことである。<strong>実データが信用できなくなる。</strong>
 */
@DisplayName("シミュレーション由来の除外")
class SimulatedDataExclusionIntegrationTest extends CargoPersistenceTestBase {

    @Autowired
    private BillableCargoFinder billable;

    @Autowired
    private com.example.bookingms.domain.repository.CargoRepository cargoRepository;

    @Autowired
    private SearchShipperUseCase searchShipper;

    @Autowired
    private com.example.bookingms.domain.repository.CancellationRequestRepository cancellations;

    /** シミュレーション由来の荷主で予約を 1 件作る。 */
    private String bookCargoFor(Long shipperId) {
        return bookCargo.book(command(shipperId,
                        com.example.bookingms.domain.model.valueobjects.CargoType.GENERAL))
                .bookingId().orElseThrow().value();
    }

    private Long simulatedShipperId(String email) {
        RegistrationOutcome outcome = registerShipper.registerAnyway(new RegisterShipperCommand(
                ShipperType.INDIVIDUAL, "シミュレーション荷主", email, "東京都千代田区 1-1-1",
                null, null, true));
        return ((RegistrationOutcome.Registered) outcome).shipper().id();
    }

    @Test
    @DisplayName("シミュレーションが作った荷主は、SIM- の帯で採番される")
    void mintsTheSimulatedBand() {
        Long id = simulatedShipperId("band@simulation.example.com");

        assertThat(searchShipper.findById(id).orElseThrow().shipperCode())
                .as("帯が分かれていないと、他の一覧はどれを外せばよいか判断できない")
                .startsWith("SIM-");
    }

    @Test
    @DisplayName("シミュレーション由来の荷主は、営業の荷主一覧に出ない")
    void hidesTheSimulatedShipperFromTheShipperList() {
        simulatedShipperId("list@simulation.example.com");
        Long real = shipperId("実在の荷主", "real-list@example.com");

        assertThat(searchShipper.search(null))
                .extracting(Shipper::shipperCode)
                .as("実在しない会社が営業の一覧に並ぶ")
                .noneMatch(code -> code.startsWith("SIM-"));
        assertThat(searchShipper.search(null))
                .as("実在の荷主まで消えている")
                .anyMatch(shipper -> shipper.id().equals(real));
    }

    /**
     * <strong>締めの一覧からは外し、名指しの照会では返す。</strong>
     *
     * <p>名指しまで外すと、シミュレーション自身の料金算出が通らなくなる——
     * 通らなければ、精算まで通ることを確かめる手段が無い。
     */
    @Test
    @DisplayName("シミュレーション由来の貨物は、精算の締め対象に出ない")
    void hidesTheSimulatedCargoFromTheBillableList() {
        Long shipper = simulatedShipperId("billing@simulation.example.com");
        Cargo delivered = deliver(shipper);

        assertThat(billable.findAllBillable())
                .extracting(BillableCargo::bookingId)
                .as("経理担当者の締めにシミュレーションの貨物が乗る")
                .doesNotContain(delivered.bookingId().orElseThrow().value());

        assertThat(billable.findBillable(delivered.bookingId().orElseThrow().value()))
                .as("名指しの照会まで外すと、シミュレーション自身の料金算出が通らない")
                .isPresent();
    }

    @Test
    @DisplayName("実在の貨物は、これまでどおり締め対象に出る")
    void keepsRealCargoInTheBillableList() {
        Cargo delivered = deliver(shipperId("実在の荷主", "real-billing@example.com"));

        assertThat(billable.findAllBillable())
                .extracting(BillableCargo::bookingId)
                .contains(delivered.bookingId().orElseThrow().value());
    }

    /** 引取まで進めた貨物を作る（締め対象になる条件）。 */
    private Cargo deliver(Long shipperId) {
        Cargo booked = bookCargo.book(command(shipperId, CargoType.GENERAL));
        jdbcTemplate.update("UPDATE cargo SET booking_status = 'DELIVERED',"
                + " last_handling_at = ? WHERE booking_id = ?",
                java.sql.Timestamp.from(Instant.parse("2030-09-19T00:00:00Z")),
                booked.bookingId().orElseThrow().value());
        return booked;
    }

    /**
     * <strong>営業の予約一覧にも出さない</strong>（IT15 のレビュー指摘）。
     *
     * <p>継続実行は一晩で数百件の予約を作る。翌朝の予約一覧がシミュレーションの
     * 予約で埋まると、営業の朝は「新しい予約を拾う」ところから始められない
     * ——一覧そのものが信用されなくなる。
     */
    @Test
    @DisplayName("シミュレーションが作った予約は、営業の予約一覧に出ない")
    void excludesSimulatedCargoFromTheBookingList() {
        // **実業務の予約も 1 件置く。** 置かないと一覧が空になり、
        // 「出ない」ことを確かめているつもりで何も確かめない
        String realBookingId = bookCargoFor(
                shipperId("実業務の荷主", "booking-list-real@example.com"));
        String bookingId = bookCargoFor(
                simulatedShipperId("booking-list@simulation.example.com"));

        List<String> listed = searchCargo.search(null, null).cargoes().stream()
                .map(summary -> summary.cargo().bookingId().orElseThrow().value())
                .toList();

        // **空でないことを先に見る。** 一覧が丸ごと壊れていても doesNotContain は
        // 通ってしまう——検査が判別しなくなる
        assertThat(listed).contains(realBookingId).doesNotContain(bookingId);
    }

    /** <strong>名指しの照会では返る。</strong>外すと、シミュレーション自身が進めない。 */
    @Test
    @DisplayName("予約番号を指定した照会では返る")
    void stillReturnsSimulatedCargoWhenAskedByBookingId() {
        Long shipperId = simulatedShipperId("booking-detail@simulation.example.com");
        String bookingId = bookCargoFor(shipperId);

        assertThat(cargoRepository.findByBookingId(bookingId)).isPresent();
    }

    /**
     * <strong>キャンセル承認待ちの一覧</strong>（TD-02・IT16）。
     *
     * <p>承認するのは追跡管理者であり、**この一覧が唯一の入口**である。架空の申請が
     * 混ざると、毎朝そこから今日やることを決める人の判断が狂う。
     */
    @Test
    @DisplayName("承認待ちの一覧に、シミュレーション由来は出ない")
    void keepsSimulatedOutOfAwaitingDecision() {
        Long real = requestCancellationFor(
                shipperId("実業務の荷主", "cancel-real@example.com"));
        Long simulated = requestCancellationFor(
                simulatedShipperId("cancel-sim@simulation.example.com"));

        assertThat(cancellations.findAwaitingDecision(100))
                .extracting(com.example.bookingms.domain.model.aggregates.CancellationRequest
                        ::cargoId)
                .as("承認待ちの一覧に架空の申請が混ざっている")
                .contains(real)
                .doesNotContain(simulated);
    }

    /**
     * <strong>陸揚げ待ちの一覧</strong>（TD-02・IT16）。
     *
     * <p>荷役作業員はここで自分の手番に気づく——作業指示は自動で作られない
     * （[ADR-025] 決定 5）。架空の案件が並ぶと、実在の貨物が指定した港を通り過ぎる。
     */
    @Test
    @DisplayName("陸揚げ待ちの一覧に、シミュレーション由来は出ない")
    void keepsSimulatedOutOfAwaitingDischarge() {
        Long real = approveCancellationFor(
                shipperId("実業務の荷主", "discharge-real@example.com"));
        Long simulated = approveCancellationFor(
                simulatedShipperId("discharge-sim@simulation.example.com"));

        assertThat(cancellations.findAwaitingDischarge(100))
                .extracting(com.example.bookingms.domain.model.aggregates.CancellationRequest
                        ::cargoId)
                .as("陸揚げ待ちの一覧に架空の案件が混ざっている")
                .contains(real)
                .doesNotContain(simulated);
    }

    /** 輸送中の貨物に、キャンセル申請を 1 件置く。 */
    private Long requestCancellationFor(Long shipperId) {
        Long cargoId = bookCargo.book(command(shipperId, CargoType.GENERAL)).id();
        // **前提を確かめる。** 予約が別の荷主に付いていると、絞りが効いていても
        // 「混ざっている」ように見え、原因を SQL 側に探すことになる
        assertThat(cargoRepository.findById(cargoId).orElseThrow().shipperId())
                .as("予約が、指定した荷主に付いていない")
                .isEqualTo(shipperId);
        cancellations.save(com.example.bookingms.domain.model.aggregates.CancellationRequest
                .request(cargoId, "荷主都合", "sales01",
                        Instant.parse("2026-09-01T00:00:00Z"),
                        com.example.bookingms.domain.model.valueobjects.BookingStatus.IN_TRANSIT,
                        true));
        return cargoId;
    }

    /** 承認済みで陸揚げ地が決まった申請を 1 件置く。 */
    private Long approveCancellationFor(Long shipperId) {
        Long cargoId = requestCancellationFor(shipperId);
        com.example.bookingms.domain.model.aggregates.CancellationRequest awaiting =
                cancellations.findLatestByCargoId(cargoId).orElseThrow();
        cancellations.updateDecision(awaiting.approve("JPTYO", "tracker01", "承認",
                Instant.parse("2026-09-02T00:00:00Z")));
        return cargoId;
    }
}
