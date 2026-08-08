package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.HandledCargo;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingDetails;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.handling.domain.model.RegisterHandlingCommand;
import com.example.cargotracker.handling.domain.model.ScannedTrackingNumber;
import com.example.cargotracker.handling.domain.repository.HandlingActivityRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 荷役作業の永続化（US15）。
 *
 * <p>SQL の正しさは実 PostgreSQL で確かめる（ADR-003）。
 *
 * <p><strong>追跡のテストと分けている。</strong> Handling は独立した BC であり
 * （ADR-010）、1 つのテストクラスから両方の型を参照すると BC 分離
 * （ArchUnit ルール 4）を破る。**ルールはテストにも等しく効く。**
 */
class HandlingRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private HandlingActivityRepository handlingRepository;

    /** 荷役作業を保存して読み戻せる。 */
    @Test
    void 荷役作業を往復できる() {
        var bookingId = new CargoBookingId(UUID.randomUUID());
        handlingRepository.save(HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20261102-0001"), bookingId),
                HandlingDetails.load(new HandlingVoyageNumber("V001")),
                Instant.parse("2026-11-02T01:00:00Z"),
                Location.of("JPOSA"), null, "港湾太郎")));

        var loaded = handlingRepository.findByBookingId(bookingId);

        assertThat(loaded).singleElement().satisfies(activity -> {
            assertThat(activity.type()).isEqualTo(HandlingType.LOAD);
            assertThat(activity.location().unlocode()).isEqualTo("JPOSA");
            // **読み戻しで落とすと、積込がどの便のものか分からなくなる**
            assertThat(activity.voyageNumber().value()).isEqualTo("V001");
            assertThat(activity.operatorName()).isEqualTo("港湾太郎");
            // **読み取った番号も往復する**（V13。IT6 レビュー H12）
            assertThat(activity.scannedTrackingNumber().value())
                    .isEqualTo("TRK-20261102-0001");
        });
    }

    /**
     * <strong>荷役履歴は新しい順で返す。</strong>
     *
     * <p>履歴は「最後に何が起きたか」を読むためのものであり、順序が崩れると
     * 現在地が読めなくなる。
     */
    @Test
    void 荷役履歴は新しい順で読み戻される() {
        var bookingId = new CargoBookingId(UUID.randomUUID());
        handlingRepository.save(HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20261101-0001"), bookingId),
                HandlingDetails.receive(), Instant.parse("2026-11-01T01:00:00Z"),
                Location.of("JPOSA"), null, "港湾太郎")));
        handlingRepository.save(HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20261102-0001"), bookingId),
                HandlingDetails.load(new HandlingVoyageNumber("V001")),
                Instant.parse("2026-11-02T01:00:00Z"),
                Location.of("JPOSA"), null, "港湾太郎")));

        assertThat(handlingRepository.findByBookingId(bookingId))
                .extracting(HandlingActivity::type)
                .containsExactly(HandlingType.LOAD, HandlingType.RECEIVE);
    }
}
