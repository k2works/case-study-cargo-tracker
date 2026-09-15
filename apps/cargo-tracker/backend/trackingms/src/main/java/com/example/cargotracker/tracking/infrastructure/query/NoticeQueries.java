package com.example.cargotracker.tracking.infrastructure.query;

import java.time.Instant;
import java.util.List;

/** 荷主への知らせの読み口（US37）。 */
public final class NoticeQueries {

    private NoticeQueries() {
    }

    /**
     * 知らせ 1 件（US37 §受入基準 1・2）。
     *
     * <p><b>行き先を持たせる。</b> 画面が追跡番号から組み立てると、書式を変えた
     * ときに黙って行き先が消える——<b>知らせが行き先を知っている</b>。</p>
     *
     * <p><b>金額も社内メモも入らない。</b> 荷主に出すのは「どの貨物が、いま
     * どうなったか」だけである。</p>
     */
    public record NoticeView(
            long sequenceNo,
            String trackingNumber,
            String statusLabel,
            String location,
            Instant occurredAt,
            String originUnLocode,
            String destinationUnLocode) {
    }

    /**
     * 未読の知らせ（US37 §受入基準 1・3）。
     *
     * @param latestSequence いちばん新しい知らせの位置。<b>画面はこれを既読として送る</b>
     *     ——画面が自分で最大値を数えると、表示を絞ったときに読み飛ばしが起きる
     */
    public record NoticeListView(List<NoticeView> items, long latestSequence) {
    }
}
