package com.example.cargotracker.tracking.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.projection.TrackingProjection;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindPublicTrackingQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.PublicTrackingEventView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 公開照会の読み口（S44 / US18）。<b>認証を要らない</b>。
 *
 * <p>荷主に見せる中身と、<b>見せない中身</b>の両方を固定する。公開画面には例外の
 * 詳細・荷主名・金額を出さない（ui_design.md）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicTrackingQueryIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");
    private static final Instant LOAD = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2026-09-24T18:00:00Z");

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingQueryHandler queries;

    private String given() {
        String trackingNumber = "TRK-Q" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                LOAD, Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"), ARRIVAL)),
                AT));
        return trackingNumber;
    }

    @Test
    @DisplayName("US18 §1: 追跡番号で照会でき、状態は呼び名で出る")
    void findsTrackingByNumber() {
        String trackingNumber = given();

        var view = queries.handle(new FindPublicTrackingQuery(trackingNumber));

        assertThat(view).isNotNull();
        // **列挙名を利用者に見せない。** NOT_RECEIVED では業務担当者に意味が分からない。
        assertThat(view.statusLabel()).isEqualTo("未受領");
        assertThat(view.originUnLocode()).isEqualTo("JPTYO");
        assertThat(view.destinationUnLocode()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("US18 §4: 到着予定は予定の旅程の最終区間の荷降し（計算式を 2 か所に置かない）")
    void derivesEstimatedArrivalFromTheLastLeg() {
        String trackingNumber = given();

        var view = queries.handle(new FindPublicTrackingQuery(trackingNumber));

        assertThat(view.estimatedArrival()).isEqualTo(ARRIVAL);
        assertThat(view.departedAt()).isEqualTo(LOAD);
    }

    @Test
    @DisplayName("US18 §3: 履歴が起きた順に出て、いまどこかが読める")
    void showsHistoryAndCurrentLocation() {
        String trackingNumber = given();
        projection.on(new TransportStatusUpdatedEvent(trackingNumber,
                TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                StatusUpdateSource.MANUAL, "JPTYO",
                Instant.parse("2026-09-10T02:00:00Z"), "tracker-1", AT), "evt-a");
        projection.on(new TransportStatusUpdatedEvent(trackingNumber,
                TransportStatus.RECEIVED, TransportStatus.LOADED,
                StatusUpdateSource.MANUAL, "SGSIN",
                Instant.parse("2026-09-11T02:00:00Z"), "tracker-1", AT), "evt-b");

        var view = queries.handle(new FindPublicTrackingQuery(trackingNumber));

        assertThat(view.history())
                .extracting(PublicTrackingEventView::statusLabel)
                .containsExactly("受領済", "積込済");
        assertThat(view.currentUnLocode()).isEqualTo("SGSIN");
    }

    @Test
    @DisplayName("見つからないときは null（存在しない番号と権限の無い番号を区別しない）")
    void doesNotDistinguishUnknownFromForbidden() {
        // 区別すると、総当たりで「実在するが自分のものではない番号」を選り分けられる。
        assertThat(queries.handle(new FindPublicTrackingQuery("TRK-NOSUCHNUM"))).isNull();
    }

    @Test
    @DisplayName("入力のゆらぎを吸収する（小文字・前後の空白・TRK- の省略）")
    void absorbsInputVariations() {
        String trackingNumber = given();
        String bare = trackingNumber.substring("TRK-".length());

        assertThat(queries.handle(new FindPublicTrackingQuery(
                "  " + trackingNumber.toLowerCase(java.util.Locale.ROOT) + "  "))).isNotNull();
        assertThat(queries.handle(new FindPublicTrackingQuery(bare))).isNotNull();
    }

    @Test
    @DisplayName("公開画面に荷主 ID・予約 ID を渡さない（画面が間違えても漏れない）")
    void doesNotExposeShipperOrBooking() {
        String trackingNumber = given();

        var view = queries.handle(new FindPublicTrackingQuery(trackingNumber));

        // **渡さないものはテストで固定する。** あとから「一覧で要るから」と足すと、
        // 公開画面にも一緒に出てしまう。
        assertThat(view.toString())
                .doesNotContain("SHP-000001")
                .doesNotContain("b-");
    }
}
