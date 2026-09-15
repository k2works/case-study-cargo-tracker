package com.example.cargotracker.tracking.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.persistence.NoticeMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.projection.TrackingProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 荷主への知らせ（US37 §受入基準 1・3・4）。
 *
 * <p><b>既読はサーバが持つ。</b> ブラウザに持つと、荷主が端末を使い分けたとき
 * 同じ知らせが行く先々でもう一度出る——この検査は<b>ブラウザを持たない</b>ので、
 * 「サーバが覚えているか」だけを見る。</p>
 *
 * <p><b>「新着」は連番で決める</b>（注 N3）。時刻で持つと、同じ時刻に複数の
 * 知らせが入ったときに取りこぼす。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NoticeQueryHandlerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private NoticeQueryHandler notices;

    @Autowired
    private NoticeMapper mapper;

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingEventMapper events;

    private String initializeTracking(String shipperId) {
        String trackingNumber = "T-N-" + System.nanoTime();
        projection.on(new TrackingInitializedEvent(trackingNumber,
                "b-n-" + System.nanoTime(), shipperId, "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"), List.of(), AT));
        return trackingNumber;
    }

    /** 知らせを 1 件作る。<b>同じ時刻で入れる</b>——時刻では区別できないことを固定する。 */
    private void notice(String trackingNumber, String status) {
        events.insert(new TrackingEventMapper.TrackingEventRow(
                "evt-" + System.nanoTime(), trackingNumber, "MANUAL", null, status,
                "JPTYO", AT, "tracking01", AT));
    }

    @Test
    @DisplayName("US37 §1: 自社の貨物の新しい知らせが出る")
    void showsNoticesForOwnCargo() {
        String shipperId = "SHP-N-" + System.nanoTime();
        String trackingNumber = initializeTracking(shipperId);
        notice(trackingNumber, "RECEIVED");
        notice(trackingNumber, "LOADED");

        var view = notices.findUnread(shipperId);

        assertThat(view.items()).hasSize(2)
                .allSatisfy(item -> assertThat(item.trackingNumber()).isEqualTo(trackingNumber));
        assertThat(view.items().get(0).statusLabel())
                .as("呼び名は列挙が持つ（画面が対応表を持つと片方だけ古くなる）")
                .isNotBlank()
                .isNotEqualTo("LOADED");
        assertThat(view.latestSequence()).isPositive();
    }

    @Test
    @DisplayName("US37 §3: 一度読んだ知らせは、もう一度出ない（サーバが覚えている）")
    void doesNotRepeatWhatWasRead() {
        String shipperId = "SHP-R-" + System.nanoTime();
        String trackingNumber = initializeTracking(shipperId);
        notice(trackingNumber, "RECEIVED");
        notice(trackingNumber, "LOADED");

        var first = notices.findUnread(shipperId);
        notices.markRead(shipperId, first.latestSequence());

        assertThat(notices.findUnread(shipperId).items())
                .as("**別の端末で開き直しても出ない**——既読はブラウザではなくここにある")
                .isEmpty();

        notice(trackingNumber, "DELIVERED");
        assertThat(notices.findUnread(shipperId).items())
                .as("そのあとに起きた知らせは出る")
                .hasSize(1);
    }

    @Test
    @DisplayName("US37 §3: 既読の位置は戻さない（古い画面が巻き戻さない）")
    void neverMovesTheReadPositionBackwards() {
        String shipperId = "SHP-B-" + System.nanoTime();
        String trackingNumber = initializeTracking(shipperId);
        notice(trackingNumber, "RECEIVED");
        long latest = notices.findUnread(shipperId).latestSequence();

        notices.markRead(shipperId, latest);
        // 別の端末の古い画面が「1 まで読んだ」と送ってくる。
        notices.markRead(shipperId, 1L);

        assertThat(mapper.findReadPosition(shipperId))
                .as("巻き戻すと、同じ知らせがもう一度出る")
                .isEqualTo(latest);
        assertThat(notices.findUnread(shipperId).items()).isEmpty();
    }

    @Test
    @DisplayName("US37 §4: 他社の貨物の知らせは、追跡番号を知っていても届かない")
    void neverLeaksAnotherShippersNotices() {
        String mine = "SHP-M-" + System.nanoTime();
        String theirs = "SHP-T-" + System.nanoTime();
        initializeTracking(mine);
        String theirTracking = initializeTracking(theirs);
        notice(theirTracking, "RECEIVED");

        assertThat(notices.findUnread(mine).items())
                .as("**絞るのは SQL。** 全件を読んでから捨てると、絞り忘れが漏れになる")
                .extracting(NoticeQueries.NoticeView::trackingNumber)
                .doesNotContain(theirTracking);
    }

    @Test
    @DisplayName("注 N3: 同じ時刻の知らせも取りこぼさない（時刻ではなく連番で決める）")
    void doesNotDropNoticesThatShareATimestamp() {
        String shipperId = "SHP-S-" + System.nanoTime();
        String trackingNumber = initializeTracking(shipperId);
        // **すべて同じ occurredAt。** 時刻で既読を持つと、ここで取りこぼす。
        notice(trackingNumber, "RECEIVED");
        notice(trackingNumber, "LOADED");
        notice(trackingNumber, "DELIVERED");

        assertThat(notices.findUnread(shipperId).items()).hasSize(3);
    }
}
