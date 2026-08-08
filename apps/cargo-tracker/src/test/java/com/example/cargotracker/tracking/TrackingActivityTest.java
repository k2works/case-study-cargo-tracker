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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 追跡レコードの不変条件（US14 / US15）。 */
@DisplayName("追跡レコード（US14 / US15）")
class TrackingActivityTest {

    private static final ZoneId 業務のタイムゾーン = ZoneId.of("Asia/Tokyo");

    private static TrackingActivity 追跡を始める() {
        return TrackingActivity.issue(
                new TrackingNumber("TRK-20260901-0001"),
                new TrackingBookingId(UUID.randomUUID()),
                com.example.cargotracker.shared.domain.model.Location.of("USLAX"),
                java.time.LocalDate.of(2026, java.time.Month.SEPTEMBER, 20));
    }

    private static TrackingActivityEvent イベント(
            TrackingEventType type, String unlocode, String at, String voyage) {
        return TrackingActivityEvent.fromHandling(
                type, Instant.parse(at), Location.of(unlocode),
                voyage == null ? null : new TrackingVoyageNumber(voyage));
    }

    /** 受入基準（US14）: 発行後、貨物状態が「受領待ち」に設定される。 */
    @Test
    void 発行直後は未受取である() {
        assertThat(追跡を始める().transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
    }

    /**
     * <strong>すべての荷役種別について、記録後の輸送状態を網羅する</strong>
     * （IT6 レビュー M7）。
     *
     * <p>IT6 では受領・積込・通関の 3 種別しか回しておらず、
     * <strong>荷降しと引取の対応づけは一度も確かめていなかった</strong>。
     * 個別のテストを 5 本並べると、種別が増えたときに足し忘れる。
     * <strong>列挙型そのものを入力にすれば、増えた瞬間にここが落ちる。</strong>
     *
     * <p>期待値は {@code TrackingEventType} から取らない。それでは
     * 「自分が言ったことを自分で確かめる」だけになり、対応づけを間違えても緑になる。
     */
    @ParameterizedTest
    @MethodSource("種別と記録後の輸送状態")
    void 種別ごとに記録後の輸送状態が決まる(
            TrackingEventType type, TransportStatus expected) {
        var tracking = 追跡を始める();

        // 航海番号は積込・荷降しでのみ意味を持つ（TrackingActivityEvent の Javadoc）
        String voyage = switch (type) {
            case LOAD, UNLOAD -> "V001";
            // 手動更新の種別（US17）は航海番号を取らない。**入港・出港は船の動きだが、
            // 追跡が持つのは貨物の位置であり、便の特定は荷役の記録が担う**
            case RECEIVE, CUSTOMS, CLAIM, DEPART, ARRIVE, AWAIT_CLAIM -> null;
        };
        tracking.recordEvent(イベント(type, "JPOSA", "2026-09-02T01:00:00Z", voyage));

        assertThat(tracking.transportStatus()).isEqualTo(expected);
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
            種別と記録後の輸送状態() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.RECEIVE, TransportStatus.RECEIVED),
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.LOAD, TransportStatus.LOADED),
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.UNLOAD, TransportStatus.UNLOADED),
                // **通関は輸送状態を動かさない。** 手続きであり、貨物は動いていない
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.CUSTOMS, TransportStatus.NOT_RECEIVED),
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.CLAIM, TransportStatus.CLAIMED),
                // 手動更新の種別（US17 / IT8）
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.DEPART, TransportStatus.ONBOARD_CARRIER),
                // **入港は輸送状態を動かさない。** 状態を変えるのは荷降ろしである
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.ARRIVE, TransportStatus.NOT_RECEIVED),
                org.junit.jupiter.params.provider.Arguments.of(
                        TrackingEventType.AWAIT_CLAIM, TransportStatus.AWAITING_CLAIM));
    }

    /**
     * <strong>網羅の表そのものが漏れていないことを確かめる。</strong>
     * 上の表に種別を書き足し忘れると、増えた種別は検査されないまま通る。
     */
    @Test
    void 網羅の表はすべての種別を含む() {
        var covered = 種別と記録後の輸送状態()
                .map(args -> (TrackingEventType) args.get()[0])
                .toList();

        assertThat(covered).containsExactlyInAnyOrder(TrackingEventType.values());
    }

    /** 受入基準（US15）: 記録後、貨物状態が対応する状態に自動更新される。 */
    @Test
    void 受領を記録すると受取済になる() {
        var tracking = 追跡を始める();

        tracking.recordEvent(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-09-02T01:00:00Z", null));

        assertThat(tracking.transportStatus()).isEqualTo(TransportStatus.RECEIVED);
    }

    @Test
    void 積込を記録すると積み込み済になる() {
        var tracking = 追跡を始める();

        tracking.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));

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
        tracking.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));

        tracking.recordEvent(イベント(TrackingEventType.CUSTOMS, "USLAX", "2026-09-20T01:00:00Z", null));

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
        tracking.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-09-03T01:00:00Z", "V001"));
        // 受領は積込より前に起きたが、入力は後になった
        tracking.recordEvent(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-09-02T01:00:00Z", null));

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
                2L,
                com.example.cargotracker.shared.domain.model.Location.of("USLAX"),
                java.time.LocalDate.of(2026, java.time.Month.SEPTEMBER, 20));

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
