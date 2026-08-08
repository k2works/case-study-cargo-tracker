package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.model.TransportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 追跡レコードの不変条件（US14 / US15）。 */
@DisplayName("追跡レコード（US14 / US15）")
class TrackingActivityTest {

    private static final ZoneId 業務のタイムゾーン = ZoneId.of("Asia/Tokyo");

    private static TrackingActivity 追跡を始める() {
        return TrackingActivity.issue(
                new TrackingNumber("TRK-20260901-0001"),
                new TrackingBookingId(UUID.randomUUID()));
    }

    private static TrackingActivityEvent イベント(
            TrackingEventType type, String unlocode, String at, String voyage) {
        return new TrackingActivityEvent(
                type, Instant.parse(at), Location.of(unlocode),
                voyage == null ? null : new TrackingVoyageNumber(voyage));
    }

    /** 受入基準（US14）: 発行後、貨物状態が「受領待ち」に設定される。 */
    @Test
    void 発行直後は未受取である() {
        assertThat(追跡を始める().transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
    }

    /** 受入基準（US15）: 記録後、貨物状態が対応する状態に自動更新される。 */
    @Test
    void 受領を記録すると受取済になる() {
        var tracking = 追跡を始める();

        tracking.record(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-09-02T01:00:00Z", null));

        assertThat(tracking.transportStatus()).isEqualTo(TransportStatus.RECEIVED);
    }

    @Test
    void 積込を記録すると積み込み済になる() {
        var tracking = 追跡を始める();

        tracking.record(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));

        assertThat(tracking.transportStatus()).isEqualTo(TransportStatus.LOADED);
    }

    /**
     * <strong>通関は輸送状態を動かさない。</strong>
     *
     * <p>貨物の位置が変わらないためである。<strong>それでもイベントは残す。</strong>
     * 記録しないと、あとから「いつ通関したのか」を追えない。
     */
    @Test
    void 通関を記録しても輸送状態は動かないがイベントは残る() {
        var tracking = 追跡を始める();
        tracking.record(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));

        tracking.record(イベント(TrackingEventType.CUSTOMS, "USLAX", "2026-09-20T01:00:00Z", null));

        assertThat(tracking.transportStatus()).isEqualTo(TransportStatus.LOADED);
        assertThat(tracking.events()).hasSize(2);
        assertThat(tracking.latestEvent().type()).isEqualTo(TrackingEventType.CUSTOMS);
    }

    /**
     * <strong>後から入力したイベントも発生日時の順に並ぶ。</strong>
     *
     * <p>現場は作業のあとでまとめて入力することがある。入力順に並べると、
     * タイムラインが実際の輸送の順序と食い違う。
     */
    @Test
    void イベントは発生日時の順に並ぶ() {
        var tracking = 追跡を始める();
        tracking.record(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));
        // 受領は積込より前に起きたが、入力は後になった
        tracking.record(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-09-02T01:00:00Z", null));

        assertThat(tracking.events())
                .extracting(TrackingActivityEvent::type)
                .containsExactly(TrackingEventType.RECEIVE, TrackingEventType.LOAD);
    }

    /**
     * <strong>輸送状態は履歴から導出せず、保存した値を復元する。</strong>
     *
     * <p>導出すると、ユニットテストが緑でもリクエストをまたいだときに巻き戻る。
     */
    @Test
    void 保存された輸送状態をそのまま復元する() {
        var tracking = TrackingActivity.reconstruct(
                new TrackingNumber("TRK-20260901-0002"),
                new TrackingBookingId(UUID.randomUUID()),
                TransportStatus.ONBOARD_CARRIER,
                List.of(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001")),
                2L);

        assertThat(tracking.transportStatus()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
        assertThat(tracking.version()).isEqualTo(2L);
    }

    /**
     * <strong>追跡番号の日付は業務のタイムゾーンで決める。</strong>
     *
     * <p>UTC で採番すると、日本時間の 0 時から 9 時のあいだに発行した番号が
     * 前日の日付になる。現場の日付と食い違う番号は、問い合わせのたびに
     * 「その番号は無い」と言われる原因になる。
     */
    @Test
    void 追跡番号の日付は業務のタイムゾーンで決まる() {
        // 2026-08-31 15:30 UTC = 2026-09-01 00:30 JST
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T15:30:00Z"), 業務のタイムゾーン);

        assertThat(TrackingNumber.issue(clock, 1).value()).isEqualTo("TRK-20260901-0001");
    }

    /**
     * <strong>連番の桁あふれは丸めない。</strong>
     *
     * <p>剰余で丸めると、同じ日に発行済みの番号と重複した番号を何事も無かったように
     * 返す。形式の検査で落として気づける形にする。
     */
    @Test
    void 連番が4桁に収まらないと発行できない() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T15:30:00Z"), 業務のタイムゾーン);

        assertThatThrownBy(() -> TrackingNumber.issue(clock, 10_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("形式");
    }

    /** 形式の違う追跡番号は受け付けない。**空文字や別体系の番号が貨物に付くのを防ぐ。** */
    @Test
    void 形式の違う追跡番号は受け付けない() {
        assertThatThrownBy(() -> new TrackingNumber("12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("形式");
    }
}
