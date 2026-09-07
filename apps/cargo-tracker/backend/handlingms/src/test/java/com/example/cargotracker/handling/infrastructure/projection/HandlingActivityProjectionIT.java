package com.example.cargotracker.handling.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 荷役の記録の投影（US15 §受入基準 4）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>は
 * 判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HandlingActivityProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant COMPLETED = Instant.parse("2026-09-16T08:30:00Z");
    private static final Instant RECORDED = Instant.parse("2026-09-16T08:35:00Z");

    @Autowired
    private HandlingActivityProjection projection;

    @Autowired
    private HandlingActivityMapper activities;

    private static HandlingActivityRegisteredEvent registered(String activityId,
            String trackingNumber, Instant completedAt, boolean offRoute) {
        return new HandlingActivityRegisteredEvent(activityId, trackingNumber, "b-1",
                "UNLOAD", "SGSIN", "V-MOL-001", offRoute, false, "handler01",
                completedAt, RECORDED);
    }

    @Test
    @DisplayName("US15 §4: 記録すると履歴に 1 行残る")
    void recordsTheActivity() {
        String activityId = "act-" + System.nanoTime();
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;

        projection.on(registered(activityId, trackingNumber, COMPLETED, false));

        var row = activities.findById(activityId);
        assertThat(row).isNotNull();
        assertThat(row.handlingType()).isEqualTo("UNLOAD");
        assertThat(row.unlocode()).isEqualTo("SGSIN");
        assertThat(row.operator()).isEqualTo("handler01");
        assertThat(row.offRoute()).isFalse();
        assertThat(row.voided()).isFalse();
    }

    @Test
    @DisplayName("US15 §7: 予定外の記録も残る（拒まない）")
    void recordsOffRouteActivity() {
        String activityId = "act-" + System.nanoTime();
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;

        projection.on(registered(activityId, trackingNumber, COMPLETED, true));

        assertThat(activities.findById(activityId).offRoute()).isTrue();
    }

    @Test
    @DisplayName("再配送で行が増えない（主キーはクライアントが作る活動 ID）")
    void isIdempotentOnRedelivery() {
        String activityId = "act-" + System.nanoTime();
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        var event = registered(activityId, trackingNumber, COMPLETED, false);

        projection.on(event);
        projection.on(event);

        assertThat(activities.findHistory(trackingNumber)).hasSize(1);
    }

    @Test
    @DisplayName("履歴は起きた順に並ぶ（記録した順ではない）")
    void ordersHistoryByWhenItHappened() {
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        // **後から入れた記録のほうが、業務上は先に起きている**（紙に控えた分）。
        projection.on(registered("act-late-" + System.nanoTime(), trackingNumber,
                Instant.parse("2026-09-17T02:00:00Z"), false));
        projection.on(registered("act-early-" + System.nanoTime(), trackingNumber,
                Instant.parse("2026-09-16T02:00:00Z"), false));

        assertThat(activities.findHistory(trackingNumber))
                .extracting(HandlingActivityMapper.HandlingActivityRow::completedAt)
                .containsExactly(Instant.parse("2026-09-16T02:00:00Z"),
                        Instant.parse("2026-09-17T02:00:00Z"));
    }

    @Test
    @DisplayName("不変条件 7: 取り消しても元の行は消えない（印が付くだけ）")
    void keepsTheRowWhenVoided() {
        String activityId = "act-" + System.nanoTime();
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        projection.on(registered(activityId, trackingNumber, COMPLETED, false));

        projection.on(new HandlingActivityVoidedEvent(activityId, trackingNumber, "b-1",
                "UNLOAD", "取り違えました", "handler01",
                Instant.parse("2026-09-16T09:00:00Z")));

        var row = activities.findById(activityId);
        assertThat(row).isNotNull();
        assertThat(row.voided()).isTrue();
        assertThat(row.voidReason()).isEqualTo("取り違えました");
        assertThat(activities.findHistory(trackingNumber))
                .as("履歴からも消えない。現場で起きたことは残る")
                .hasSize(1);
    }

    @Test
    @DisplayName("S50: この航海のこの港で送信済みの記録を新しい順に引ける")
    void findsRecentActivitiesOnVoyage() {
        String voyage = "V-S50-" + System.nanoTime() % 100000L;
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        projection.on(new HandlingActivityRegisteredEvent("act-" + System.nanoTime(),
                trackingNumber, "b-1", "UNLOAD", "SGSIN", voyage, false, false,
                "handler01", COMPLETED, RECORDED));

        assertThat(activities.findOnVoyage(voyage, "SGSIN", 50))
                .extracting(HandlingActivityMapper.HandlingActivityRow::trackingNumber)
                .contains(trackingNumber);
    }
}
