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

    /**
     * 何が起きたかの呼び名。
     *
     * <p><b>状態だけでは足りない</b>（US37 §受入基準 1）。例外の起票も解決も
     * 状態は「例外発生」なので、種別を出さないと同じ文面で届く。</p>
     *
     * <p><b>知らない種別は素のまま出す。</b> 消すと、知らせそのものが
     * 「何も書いていない行」になる。</p>
     */
    private static final java.util.Map<String, String> EVENT_LABELS = java.util.Map.of(
            "HANDLING", "荷役の記録",
            "MANUAL", "状態の更新",
            "MISROUTE", "誤配",
            "EXCEPTION", "異常の発生",
            "RESOLVED", "異常の解決",
            "VOIDED", "記録の取り消し",
            "NOT_APPLIED", "反映できない荷役",
            "DEFERRED", "預かった荷役",
            "CLOSED", "追跡の終了");

    /**
     * 未読の知らせ（古い順）。
     *
     * <p><b>初めて開いた荷主には何も出さない。</b> 既読位置が無いと過去の履歴が
     * すべて未読になり、何か月も前の積込・荷降しが出る——最初の体験が
     * 「古い知らせの山を閉じる作業」になる（IT17 のレビューで実測）。
     * <b>いまを起点にする</b>。</p>
     */
    public NoticeQueries.NoticeListView findUnread(String shipperId) {
        if (notices.findReadPosition(shipperId) == null) {
            Long latest = notices.findLatestSequence(shipperId);
            notices.markRead(shipperId, latest == null ? 0L : latest, clock.instant());
            return new NoticeQueries.NoticeListView(List.of(), latest == null ? 0L : latest);
        }
        List<NoticeMapper.NoticeRow> rows = notices.findUnread(shipperId, MAX_NOTICES);
        List<NoticeQueries.NoticeView> items = rows.stream()
                .map(NoticeQueryHandler::toView)
                .toList();
        // **出した中でいちばん新しい位置を返す。** 古い順に引いているので、
        // これを既読にすると<b>出した分ちょうど</b>が読まれたことになる
        // ——新しい順だと、返る 20 件の最大値が未読全体の最大値になり、
        // 出していない古い知らせまで黙って既読になる（IT17 のレビューで実測）。
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
                EVENT_LABELS.getOrDefault(row.eventType(), row.eventType()),
                TransportStatus.valueOf(row.newStatus()).label(), row.location(),
                row.occurredAt(), row.originUnlocode(), row.destinationUnlocode());
    }
}
