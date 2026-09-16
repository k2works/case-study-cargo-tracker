package com.example.cargotracker.booking.infrastructure.projection;

import com.example.cargotracker.booking.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.booking.infrastructure.persistence.QuotationMapper;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 見積の投影（US01 §受入基準 4）。
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される
 * （[ADR-0001] 決定 6）。</p>
 *
 * <p><b>候補は入れ直す。</b> 追記専用の行はリプレイで増える（IT6 で実際に踏んだ）。
 * 見積の本体は作られたあと変わらないので、衝突したら見送る。</p>
 *
 * <p><b>処理の列を見積ごとに分ける</b>（[ADR-0014] 決定 4）。列が全体で 1 本だと、
 * 1 件の不正なイベントで別の見積まで届かなくなる。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "quotationId")
@Component
public class QuotationProjection {

    private final QuotationMapper quotations;
    private final Clock clock;

    public QuotationProjection(QuotationMapper quotations, Clock clock) {
        this.quotations = quotations;
        this.clock = clock;
    }

    @EventHandler
    public void on(QuotationCreatedEvent event) {
        quotations.insert(new QuotationMapper.QuotationRow(
                event.quotationId(), event.originUnLocode(), event.destinationUnLocode(),
                event.arrivalDeadline(), event.cargoType(), event.weightKg(),
                event.estimatedAmount(), event.currency(), event.validUntil(),
                event.createdBy(), event.createdAt(), clock.instant()));

        // **入れ直す。** 読み直しても候補が積み上がらない（追記専用の行は
        // リプレイで増える）。
        quotations.deleteCandidates(event.quotationId());
        for (QuotationCreatedEvent.Candidate candidate : event.candidates()) {
            quotations.insertCandidate(new QuotationMapper.CandidateRow(
                    event.quotationId(), candidate.candidateSeq(), candidate.voyageNumbers(),
                    candidate.ports(),
                    candidate.transitDays(), candidate.estimatedCost(), candidate.currency(),
                    candidate.overdueDays()));
        }
    }
}
