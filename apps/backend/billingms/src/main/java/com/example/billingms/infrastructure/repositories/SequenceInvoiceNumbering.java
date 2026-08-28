package com.example.billingms.infrastructure.repositories;

import com.example.billingms.application.port.InvoiceNumbering;
import com.example.billingms.domain.model.InvoiceId;
import java.time.Clock;
import java.time.ZoneId;

/**
 * 請求番号の採番（[ADR-011] と同じ形）。
 *
 * <p><strong>DB のシーケンスに任せる。</strong>MAX+1 の自前採番は、同時に 2 件発行された
 * ときに衝突する——テストでも同じ経路を使う（自前採番を書くと、シーケンスと衝突して
 * 原因でないテストが落ちる）。
 *
 * <p>形は {@code INV-YYYY} + 6 桁。予約番号（{@code BKG-YYYY} + 6 桁）と揃える。
 */
public class SequenceInvoiceNumbering implements InvoiceNumbering {

    private final InvoiceMapper mapper;
    private final Clock clock;

    public SequenceInvoiceNumbering(InvoiceMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public InvoiceId next() {
        long sequence = mapper.nextInvoiceNumber();
        // **年は業務タイムゾーンで決める。** UTC で決めると、年末年始の数時間だけ
        // 前年の番号が出る
        int year = java.time.LocalDate.ofInstant(clock.instant(), businessZone()).getYear();
        return InvoiceId.of("INV-%d%06d".formatted(year, sequence));
    }

    private ZoneId businessZone() {
        return clock.getZone();
    }
}
