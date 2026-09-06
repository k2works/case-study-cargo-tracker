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
 * 追跡の投影（US14）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>は
 * 判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingProjectionIT extends AbstractAxonIntegrationTest {

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
    @DisplayName("US14 §3: 追跡を作ると貨物状態が未受領になり、予約から引ける")
    void createsTrackingWithNotReceived() {
        String trackingNumber = "T-P-" + System.nanoTime();
        String bookingId = "b-" + System.nanoTime();

        projection.on(initialized(trackingNumber, bookingId));

        var row = trackings.findByTrackingNumber(trackingNumber);
        assertThat(row.transportStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(row.originUnlocode()).isEqualTo("JPTYO");
        assertThat(row.destinationUnlocode()).isEqualTo("USNYC");
        assertThat(row.cargoType()).isEqualTo("GENERAL");
        assertThat(trackings.findByBooking(bookingId))
                .as("連鎖が通ったかは予約から引いて確かめる")
                .isNotNull();
    }

    @Test
    @DisplayName("US14: 予定の旅程が積む順に残る（IT9 の荷役が予定と実績を照合する）")
    void keepsTheItineraryInOrder() {
        // **落としても集約の検査は緑のまま。** コマンド → イベント → 投影の
        // どこで落ちても分かるように、投影から読み直す。
        String trackingNumber = "T-P-" + System.nanoTime();

        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        assertThat(trackings.findLegs(trackingNumber))
                .extracting(TrackingSummaryMapper.TrackingLegRow::voyageNumber)
                .containsExactly("V-MOL-001", "V-ONE-002");
    }

}
