package com.example.trackingms.application.port;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.Optional;

/** 追跡の保存先（出力ポート）。 */
public interface TrackingActivityRepository {

    /**
     * まだ無ければ保存し、すでにあればそれを返す。<strong>1 回の書き込みで決める</strong>。
     *
     * <p>「探してから無ければ保存する」形にすると、2 つのイベントが同時に届いたときに
     * どちらも「無い」と読んでから書き、一意制約に当たった側が落ちる。単一の購読者では
     * 表面化しないが、<strong>並行化した瞬間に壊れる</strong>（[ADR-022] 決定 5）。
     *
     * <p>重複かどうかを決めるのは DB の一意制約であり、事前の読み出しではない。
     */
    TrackingActivity saveIfAbsent(TrackingActivity activity);

    /**
     * 追跡の状態を更新する（US15-4）。
     *
     * <p><strong>作成とは別のメソッドにする。</strong>「常に INSERT する save」で更新まで
     * 賄うと、最初の更新のときに行が増える。IT6 では作成しか起きなかったため、
     * 分岐が無いことを明記して先送りしていた。
     */
    void updateStatus(TrackingActivity activity);

    /** 追跡番号から探す。照会の入口であり、二重に作らないための確認にも使う。 */
    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);
}
