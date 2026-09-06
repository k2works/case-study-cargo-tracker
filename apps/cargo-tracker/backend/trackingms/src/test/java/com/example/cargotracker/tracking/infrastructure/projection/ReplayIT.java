package com.example.cargotracker.tracking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
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
 * <p>ArchUnit はコンパイル時の依存しか見ておらず、<b>実行時に呼ばれないことの保証では
 * ない</b>。ここでは投影のハンドラをもう一度流し、副作用が積み上がらないことを確かめる。</p>
 *
 * <p><b>「行が増えない」だけでは足りない。</b> 追跡そのものは主キーで上書きになるが、
 * <b>旅程は追記の表</b>である。消してから入れ直さないと、リプレイのたびに区間が倍に
 * なり、荷役（IT9）が予定と実績を照合できなくなる（IT6 の「追記専用の行はリプレイで
 * 増える」）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingSummaryMapper trackings;

    private static TrackingInitializedEvent initialized(String trackingNumber, String bookingId) {
        return new TrackingInitializedEvent(trackingNumber, bookingId, "JPTYO", "USNYC",
                "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("追跡開始を 2 度読んでも追跡は 1 つで、区間も倍にならない")
    void replayingInitializationDoesNotDuplicate() {
        String trackingNumber = "T-R-" + System.nanoTime();
        String bookingId = "b-" + System.nanoTime();

        projection.on(initialized(trackingNumber, bookingId));
        projection.on(initialized(trackingNumber, bookingId));

        assertThat(trackings.findByTrackingNumber(trackingNumber)).isNotNull();
        assertThat(trackings.findLegs(trackingNumber))
                .as("追記だけにすると、リプレイで区間が倍になる")
                .hasSize(2);
    }
}
