package com.example.cargotracker.routing.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 経路割り当て画面の表示用データ（US08）。
 *
 * <p><strong>画面が判断を持たないようにする。</strong> 選べるか・期限を満たすか・
 * 何位かは、ここまでで決まっている。
 *
 * @param bookingId       予約 ID
 * @param shipperName     荷主名
 * @param origin          出発地
 * @param destination     目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoTypeLabel  貨物種別の表示名
 * @param weightKilograms 重量（キログラム）
 * @param calculated      算出済みか。まだなら候補は空である
 * @param candidates      候補（推奨順）
 */
public record RouteProposalView(
        String bookingId,
        String shipperName,
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        String cargoTypeLabel,
        BigDecimal weightKilograms,
        boolean calculated,
        List<Candidate> candidates) {

    public RouteProposalView {
        candidates = List.copyOf(candidates);
    }

    /** 候補が 1 件も無いか。**算出前と区別する。** */
    public boolean hasNoCandidate() {
        return calculated && candidates.isEmpty();
    }

    /**
     * 期限内に着ける候補が 1 件も無いか。
     *
     * <p>候補ゼロとは<strong>区別する</strong>。便はあるが間に合わないのか、
     * 便そのものが無いのかで、次にすべきことが違う。
     */
    public boolean hasNoDeadlineSatisfyingCandidate() {
        return calculated && !candidates.isEmpty()
                && candidates.stream().noneMatch(Candidate::deadlineSatisfied);
    }

    /**
     * 候補 1 件。
     *
     * @param priority           表示順
     * @param voyageNumber       航海番号
     * @param transitPortsLabel  経由港。直行は「直行」
     * @param departureTime      出発日時
     * @param arrivalTime        到着日時
     * @param transitDays        所要日数
     * @param estimatedCost      概算費用（ADR-008）
     * @param currency           通貨
     * @param deadlineSatisfied  希望期限を満たすか
     * @param selectable         選べるか
     * @param unselectableReason 選べない理由。選べるなら {@code null}
     */
    public record Candidate(
            int priority,
            String voyageNumber,
            String transitPortsLabel,
            Instant departureTime,
            Instant arrivalTime,
            int transitDays,
            BigDecimal estimatedCost,
            String currency,
            boolean deadlineSatisfied,
            boolean selectable,
            String unselectableReason) {
    }
}
