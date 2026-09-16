package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.domain.model.events.CancellationApprovedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRejectedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRequestedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.CancellationRequestMapper;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * キャンセル申請の投影（UC22 / US30。IT15 T3）。
 *
 * <p><b>記録と読み口は対で出す。</b> 集約が申請を持っているのは「二重に申請させない」
 * ためで、<b>人が読む口はここ</b>——書かなければ、追跡管理者は申請が来たことを
 * 知る手段を持たない。</p>
 *
 * <p><b>行は消さない。</b> 却下された申請も残す——「いつ誰が何を断ったか」は、
 * 同じ予約でもう一度申請するときに読む材料になる。承認待ちの一覧から外れるのは
 * {@code decision} が入るからで、消したからではない。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class CancellationProjection {

    private static final Logger log = LoggerFactory.getLogger(CancellationProjection.class);

    private final CancellationRequestMapper requests;
    private final Clock clock;

    public CancellationProjection(CancellationRequestMapper requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @EventHandler
    public void on(CancellationRequestedEvent event) {
        requests.insert(new CancellationRequestMapper.CancellationRequestRow(
                event.requestId(), event.bookingId(), event.reason(),
                event.requestedBy(), event.requestedAt(), null, null, null, null, null));
    }

    @EventHandler
    public void on(CancellationApprovedEvent event) {
        decide(event.requestId(), "APPROVED", event.dischargeUnLocode(), event.reason(),
                event.approvedBy(), event.approvedAt());
    }

    @EventHandler
    public void on(CancellationRejectedEvent event) {
        decide(event.requestId(), "REJECTED", null, event.reason(),
                event.rejectedBy(), event.rejectedAt());
    }

    /**
     * 判断を写す。
     *
     * <p><b>書けなかったことを黙らない。</b> 0 行なら、申請の行が無いか
     * すでに判断済みである——どちらも「承認待ちに残り続ける」形で人に見えるので、
     * 原因を追える跡を残す。</p>
     */
    private void decide(String requestId, String decision, String dischargeUnLocode,
            String reason, String decidedBy, java.time.Instant decidedAt) {
        int updated = requests.decide(requestId, decision, dischargeUnLocode, reason,
                decidedBy, decidedAt, clock.instant());
        if (updated == 0) {
            log.warn("判断を書ける申請が投影に無い（または判断済み）: requestId={} decision={}",
                    requestId, decision);
        }
    }
}
