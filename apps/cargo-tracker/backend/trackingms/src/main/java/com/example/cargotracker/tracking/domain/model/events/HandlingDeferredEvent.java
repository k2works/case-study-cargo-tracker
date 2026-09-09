package com.example.cargotracker.tracking.domain.model.events;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 例外の対応中に届いた荷役を、いったん預かった（IT11 引き継ぎ枠 B）。
 *
 * <p><b>{@code HandlingNotAppliedEvent} と分ける。</b> 遷移表が許さない荷役は
 * 「起きえない順序で届いた」ものなので、あとから適用してはいけない。例外の対応中に
 * 届いた荷役は<b>順序としては正しく、ただ状態が例外に退避しているだけ</b>で、
 * 解決したら適用しなければならない。同じイベントで表すと、どちらの意味だったかを
 * あとから見分けられず、片方を直すともう片方が壊れる。</p>
 *
 * <p>IT10 まではどちらも「適用しない」で終わっており、<b>解決したあとの状態が
 * 事実と食い違ったまま残っていた</b>——船に積んだ貨物が受領済に見える。</p>
 *
 * <p><b>適用に要るものを全部運ぶ。</b> 預かった荷役をあとから適用するのは
 * 集約自身なので、作業者・場所・時刻まで載せないと、再適用した履歴が
 * 「誰がやったか分からない荷役」になる。</p>
 */
public record HandlingDeferredEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String activityId,
        String handlingType,
        String unLocode,
        boolean finalPort,
        boolean offRoute,
        TransportStatus currentStatus,
        TransportStatus attemptedStatus,
        String operator,
        Instant completedAt,
        Instant deferredAt) {
}
