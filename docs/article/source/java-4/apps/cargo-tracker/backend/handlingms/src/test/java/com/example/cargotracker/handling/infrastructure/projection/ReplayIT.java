package com.example.cargotracker.handling.infrastructure.projection;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 投影のリプレイ（[ADR-0001] コンプライアンス「投影がコマンドを送らない」）。
 *
 * <p>ArchUnit はコンパイル時の依存しか見ておらず、<b>実行時に呼ばれないことの
 * 保証ではない</b>。ここでは投影のハンドラをもう一度流し、副作用が積み上がらない
 * ことを確かめる。</p>
 *
 * <p><b>「行が増えない」だけでは足りない。</b> 貨物の写しは主キーで上書きになるが、
 * <b>旅程は追記の表</b>である。消してから入れ直さないと、リプレイのたびに区間が
 * 倍になり、予定ルートの判定が壊れる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private CargoSnapshotProjection projection;

    @Autowired
    private CargoSnapshotMapper cargos;

    private static TrackingInitializedEvent initialized(String trackingNumber) {
        return new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("貨物の写しを 2 度読んでも、行も区間も増えない")
    void replayingDoesNotDuplicate() {
        String trackingNumber = "TRK-R" + System.nanoTime() % 1000000000L;
        var event = initialized(trackingNumber);

        projection.on(event, "evt-replay");
        projection.on(event, "evt-replay");

        assertThat(cargos.findByTrackingNumber(trackingNumber)).isNotNull();
        assertThat(cargos.findLegs(trackingNumber))
                .as("旅程は追記の表。消してから入れ直さないとリプレイで倍になる")
                .hasSize(2);
    }

    @Test
    @DisplayName("旅程が組み直されたら、古い区間は残らない")
    void replacesTheItinerary() {
        String trackingNumber = "TRK-R" + System.nanoTime() % 1000000000L;
        projection.on(initialized(trackingNumber), "evt-1");

        projection.on(new TrackingInitializedEvent(trackingNumber, "b-1", "SHP-000001",
                "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-DIRECT-9", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                AT), "evt-2");

        assertThat(cargos.findLegs(trackingNumber))
                .extracting(CargoSnapshotMapper.CargoSnapshotLegRow::voyageNumber)
                .containsExactly("V-DIRECT-9");
    }
}
