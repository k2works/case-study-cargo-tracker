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
        Long shipperId = simulatedShipperId("booking-list@simulation.example.com");
        String bookingId = bookCargoFor(shipperId);

        assertThat(searchCargo.search(null, null).cargoes())
                .extracting(summary -> summary.cargo().bookingId().orElseThrow().value())
                .doesNotContain(bookingId);
    }

    /** <strong>名指しの照会では返る。</strong>外すと、シミュレーション自身が進めない。 */
    @Test
    @DisplayName("予約番号を指定した照会では返る")
    void stillReturnsSimulatedCargoWhenAskedByBookingId() {
        Long shipperId = simulatedShipperId("booking-detail@simulation.example.com");
        String bookingId = bookCargoFor(shipperId);

        assertThat(cargoRepository.findByBookingId(bookingId)).isPresent();
    }
}
