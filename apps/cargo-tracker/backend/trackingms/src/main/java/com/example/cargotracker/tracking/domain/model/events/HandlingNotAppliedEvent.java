package com.example.cargotracker.tracking.domain.model.events;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 届いた荷役を貨物状態へ反映できなかった（IT9 レビュー M6）。
 *
 * <p><b>無言で捨てない。</b> 遷移表が許さない荷役（順序の入れ替わり、例外の対応中）
 * は状態を動かせないが、<b>届いたこと自体は事実</b>である。捨てると、追跡の履歴には
 * 何も残らず、「荷役は記録したのに追跡が動いていない」という問い合わせに答えられない
 * ——記録は handlingms にあるが、追跡管理者はそちらを見る手がかりを持たない。</p>
 *
 * <p>状態は動かさない。投影（{@code tracking_event}）に履歴として残すだけである。</p>
 */
public record HandlingNotAppliedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String activityId,
        String handlingType,
        String unLocode,
        TransportStatus currentStatus,
        TransportStatus attemptedStatus,
        Instant completedAt,
        Instant recordedAt) {
}
