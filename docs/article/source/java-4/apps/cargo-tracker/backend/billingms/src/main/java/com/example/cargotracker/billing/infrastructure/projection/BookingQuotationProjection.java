package com.example.cargotracker.billing.infrastructure.projection;

import com.example.cargotracker.billing.infrastructure.persistence.BookingQuotationMapper;
import com.example.cargotracker.shared.contract.event.CargoQuotedEvent;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 予約のもとになった見積を写す（注 N12）。
 *
 * <p><b>算出のたびに bookingms へ問い合わせない。</b> 相手が落ちている間は
 * 請求書が作れなくなる（ADR-0012 と同じ形）。</p>
 *
 * <p><b>予約の時点で届く。</b> 請求書を作るのは引取のあとなので、それまでに
 * 十分間に合う。届いていなければ概算が無いだけで、請求そのものは作れる。</p>
 *
 * <p><b>処理の列を予約ごとに分ける</b>（[ADR-0014] 決定 4）。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
@Component
public class BookingQuotationProjection {

    private final BookingQuotationMapper quotations;
    private final Clock clock;

    public BookingQuotationProjection(BookingQuotationMapper quotations, Clock clock) {
        this.quotations = quotations;
        this.clock = clock;
    }

    @EventHandler
    public void on(CargoQuotedEvent event) {
        quotations.upsert(new BookingQuotationMapper.QuotationRow(
                event.bookingId(), event.quotationId(), event.quotedAmount(),
                event.currency(), event.quotedAt(), clock.instant()));
    }
}
