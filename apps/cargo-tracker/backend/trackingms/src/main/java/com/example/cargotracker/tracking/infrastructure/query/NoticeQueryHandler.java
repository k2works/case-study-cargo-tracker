package com.example.cargotracker.tracking.infrastructure.query;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.NoticeMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 荷主への知らせ（US37）。
 *
 * <p><b>呼び名は列挙が持つ。</b> 画面が対応表を持つと、値を足したときに片方だけが
 * 古くなる。</p>
 */
@Component
public class NoticeQueryHandler {

    /** 一度に出す上限。<b>ポップアップなので多くは出さない</b>。 */
    private static final int MAX_NOTICES = 20;

    private final NoticeMapper notices;
    private final Clock clock;

    public NoticeQueryHandler(NoticeMapper notices, Clock clock) {
        this.notices = notices;
        this.clock = clock;
    }

    /** 未読の知らせ（新しい順）。 */
    public NoticeQueries.NoticeListView findUnread(String shipperId) {
        List<NoticeMapper.NoticeRow> rows = notices.findUnread(shipperId, MAX_NOTICES);
        List<NoticeQueries.NoticeView> items = rows.stream()
                .map(NoticeQueryHandler::toView)
                .toList();
        // **いちばん新しい位置はサーバが決める。** 画面が数えると、上限で
        // 切れたときに「出していない知らせまで既読」にしてしまう——ここで返す
        // のは「出した中でいちばん新しいもの」である。
        long latest = rows.stream().mapToLong(NoticeMapper.NoticeRow::sequenceNo).max()
                .orElse(0L);
        return new NoticeQueries.NoticeListView(items, latest);
    }

    /** ここまで読んだ（§3）。<b>戻さない</b>（別の端末が先に読んでいれば、そちらが正）。 */
    public void markRead(String shipperId, long sequenceNo) {
        notices.markRead(shipperId, sequenceNo, clock.instant());
    }

    private static NoticeQueries.NoticeView toView(NoticeMapper.NoticeRow row) {
        return new NoticeQueries.NoticeView(row.sequenceNo(), row.trackingNumber(),
                TransportStatus.valueOf(row.newStatus()).label(), row.location(),
                row.occurredAt(), row.originUnlocode(), row.destinationUnlocode());
    }
}
