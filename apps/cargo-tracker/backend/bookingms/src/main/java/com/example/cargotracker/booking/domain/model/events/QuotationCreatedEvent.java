package com.example.cargotracker.booking.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送見積を作った（bookingms の内部イベント / US01）。
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約が空のまま復元され、
 * 状態を見る守りが素通りする（{@code AxonTestFixture} では判別できない）。</p>
 *
 * <p><b>投影が作れる分を運ぶ。</b> 投影はコマンドを読まないので、
 * {@code quotation} と {@code quotation_candidate} の列がここに揃っていなければ
 * 画面に出せない。値オブジェクトではなく<b>素の値</b>で載せる——契約ではないが、
 * 投影が読む形に合わせておくほうが、復元とマッピングの両方で素直になる。</p>
 *
 * @param candidates 概算つきのルート候補。<b>空でもよい</b>（期限に間に合う
 *     経路が無いことも荷主に伝えるべき答え）
 */
public record QuotationCreatedEvent(
        @EventTag(key = "quotationId") String quotationId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        String hazardousImoClass,
        String hazardousUnNumber,
        BigDecimal estimatedAmount,
        String currency,
        LocalDate validUntil,
        List<Candidate> candidates,
        String createdBy,
        Instant createdAt) {

    public QuotationCreatedEvent {
        // **null の一覧でも壊れない**（追記専用の形を守る）。古い記録を読めなく
        // しないために、復元では断らず既定に落とす。
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * ルート候補 1 件。
     *
     * <p>正典の {@code quotation_candidate} は経路そのものを持たず、航海番号の
     * 並びだけを持つ（経路は予約のとき {@code cargo_leg} に写す）。</p>
     *
     * @param overdueDays 希望期限からの超過日数。0 なら間に合う
     */
    public record Candidate(
            int candidateSeq,
            String voyageNumbers,
            int transitDays,
            BigDecimal estimatedCost,
            String currency,
            int overdueDays) {
    }
}
