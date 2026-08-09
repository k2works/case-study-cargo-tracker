package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.RelaxationRequest;
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
 * @param searchDeadline  <strong>いま探索に使っている</strong>希望到着期限（US10 で延ばした後の値）
 * @param maxTransitCount いま探索に使っている経由回数の上限
 * @param extraDays       当初の期限から延ばした日数。0 なら延ばしていない
 * @param candidates      候補（推奨順）
 * @param misroutedFrom   <strong>誤配のときの貨物の現在地</strong>（US28）。
 *                        誤配でなければ {@code null}。<strong>ここから引き直す</strong>
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
        LocalDate searchDeadline,
        int maxTransitCount,
        long extraDays,
        List<Candidate> candidates,
        String misroutedFrom) {

    public RouteProposalView {
        candidates = List.copyOf(candidates);
    }

    /**
     * 誤配のため引き直すのか（US28）。
     *
     * <p><strong>画面が判断を持たないようにする。</strong> 「現在地が入っていれば」と
     * 画面に書くと、同じ規則が 2 か所に散る。
     */
    public boolean isMisrouted() {
        return misroutedFrom != null && !misroutedFrom.isBlank();
    }

    /**
     * 探索の出発地。
     *
     * <p><strong>誤配のときは現在地から引き直す</strong>（受入基準）。予約の出発地から
     * 引き直すと、すでに動いた分をなかったことにした経路が出る。
     */
    public String searchOrigin() {
        return isMisrouted() ? misroutedFrom : origin;
    }

    /**
     * 期限を延ばして探しているか。
     *
     * <p><strong>記録するだけでは誰も気づかない。</strong> 延ばした事実は画面に出し、
     * 荷主への通知（US12）にも載せる。
     */
    public boolean deadlineRelaxed() {
        return extraDays > 0;
    }

    /** これ以上延ばせる日数。**上限に当たっていることを画面で示すために使う。** */
    public long remainingExtraDays() {
        return Math.max(0, RelaxationRequest.MAX_EXTRA_DAYS - extraDays);
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
     * @param capacityAvailable  空き容量があるか（US09 / IT5）
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
            boolean capacityAvailable,
            boolean deadlineSatisfied,
            boolean selectable,
            String unselectableReason,
            long daysOverDeadline) {

        /**
         * 当初の希望期限を超えるか（US28）。
         *
         * <p><strong>「期限を過ぎます」だけでは足りない。</strong> 1 日なのか 2 週間なのかで、
         * 荷主への説明も、代替を探すかの判断も変わる。
         */
        public boolean overshootsDeadline() {
            return daysOverDeadline > 0;
        }
    }
}
