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
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 13 個の要素が一列に並び、{@code arrivalDeadline} と
 * {@code searchDeadline} という<strong>「予約の期限」と「探索に使った期限」</strong>が
 * 同じ型で隣り合っていた — 取り違えると、延ばして探した結果を
 * 「元の期限で探した結果」として読ませてしまう。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param bookingId 予約 ID
 * @param cargo     対象の貨物（誰の・何を・どこからどこへ・いつまでに）
 * @param criteria  いま探索に使っている条件
 * @param result    算出の結果
 */
public record RouteProposalView(
        String bookingId,
        CargoSummary cargo,
        SearchCriteria criteria,
        Result result) {

    /**
     * 対象の貨物。
     *
     * @param shipperName     荷主名
     * @param origin          出発地
     * @param destination     目的地
     * @param arrivalDeadline 希望到着期限（<strong>予約の期限。探索に使う期限とは別</strong>）
     * @param cargoTypeLabel  貨物種別の表示名
     * @param weightKilograms 重量（キログラム）
     * @param misroutedFrom   <strong>誤配のときの貨物の現在地</strong>（US28）。
     *                        誤配でなければ {@code null}。<strong>ここから引き直す</strong>
     */
    public record CargoSummary(
            String shipperName,
            String origin,
            String destination,
            LocalDate arrivalDeadline,
            String cargoTypeLabel,
            BigDecimal weightKilograms,
            String misroutedFrom) {

        /** 誤配のため引き直すのか（US28）。 */
        public boolean isMisrouted() {
            return misroutedFrom != null && !misroutedFrom.isBlank();
        }
    }

    /**
     * いま探索に使っている条件。
     *
     * @param deadline        <strong>いま探索に使っている</strong>希望到着期限
     *                        （US10 で延ばした後の値）
     * @param maxTransitCount 経由回数の上限
     * @param extraDays       当初の期限から延ばした日数。0 なら延ばしていない
     */
    public record SearchCriteria(LocalDate deadline, int maxTransitCount, long extraDays) { }

    /**
     * 算出の結果。
     *
     * @param calculated 算出済みか。まだなら候補は空である
     * @param candidates 候補（推奨順）
     */
    public record Result(boolean calculated, List<Candidate> candidates) {

        public Result {
            candidates = List.copyOf(candidates);
        }
    }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 荷主名 */
    public String shipperName() {
        return cargo.shipperName();
    }

    /** @return 出発地 */
    public String origin() {
        return cargo.origin();
    }

    /** @return 目的地 */
    public String destination() {
        return cargo.destination();
    }

    /** @return 希望到着期限（予約の期限） */
    public LocalDate arrivalDeadline() {
        return cargo.arrivalDeadline();
    }

    /** @return 貨物種別の表示名 */
    public String cargoTypeLabel() {
        return cargo.cargoTypeLabel();
    }

    /** @return 重量（キログラム） */
    public BigDecimal weightKilograms() {
        return cargo.weightKilograms();
    }

    /** @return 誤配のときの貨物の現在地（US28） */
    public String misroutedFrom() {
        return cargo.misroutedFrom();
    }

    /** @return 探索に使っている希望到着期限 */
    public LocalDate searchDeadline() {
        return criteria.deadline();
    }

    /** @return 探索に使っている経由回数の上限 */
    public int maxTransitCount() {
        return criteria.maxTransitCount();
    }

    /** @return 当初の期限から延ばした日数 */
    public long extraDays() {
        return criteria.extraDays();
    }

    /** @return 算出済みか */
    public boolean calculated() {
        return result.calculated();
    }

    /** @return 候補（推奨順） */
    public List<Candidate> candidates() {
        return result.candidates();
    }

    /**
     * 誤配のため引き直すのか（US28）。
     *
     * <p><strong>画面が判断を持たないようにする。</strong> 「現在地が入っていれば」と
     * 画面に書くと、同じ規則が 2 か所に散る。
     */
    public boolean isMisrouted() {
        return cargo.isMisrouted();
    }

    /**
     * 探索の出発地。
     *
     * <p><strong>誤配のときは現在地から引き直す</strong>（受入基準）。予約の出発地から
     * 引き直すと、すでに動いた分をなかったことにした経路が出る。
     */
    public String searchOrigin() {
        return isMisrouted() ? cargo.misroutedFrom() : cargo.origin();
    }

    /**
     * 期限を延ばして探しているか。
     *
     * <p><strong>記録するだけでは誰も気づかない。</strong> 延ばした事実は画面に出し、
     * 荷主への通知（US12）にも載せる。
     */
    public boolean deadlineRelaxed() {
        return criteria.extraDays() > 0;
    }

    /** これ以上延ばせる日数。**上限に当たっていることを画面で示すために使う。** */
    public long remainingExtraDays() {
        return Math.max(0, RelaxationRequest.MAX_EXTRA_DAYS - criteria.extraDays());
    }

    /** 候補が 1 件も無いか。**算出前と区別する。** */
    public boolean hasNoCandidate() {
        return result.calculated() && result.candidates().isEmpty();
    }

    /**
     * 期限内に着ける候補が 1 件も無いか。
     *
     * <p>候補ゼロとは<strong>区別する</strong>。便はあるが間に合わないのか、
     * 便そのものが無いのかで、次にすべきことが違う。
     */
    public boolean hasNoDeadlineSatisfyingCandidate() {
        return result.calculated() && !result.candidates().isEmpty()
                && result.candidates().stream().noneMatch(Candidate::deadlineSatisfied);
    }

    /**
     * 候補 1 件。
     *
     * @param priority           表示順
     * @param voyageNumber       航海番号
     * @param transitPortsLabel  経由港。直行は「直行」
     * @param schedule           日程
     * @param cost               費用
     * @param availability       選べるかどうか
     */
    public record Candidate(
            int priority,
            String voyageNumber,
            String transitPortsLabel,
            Schedule schedule,
            Cost cost,
            Availability availability) {

        /**
         * 日程。
         *
         * @param departureTime 出発日時
         * @param arrivalTime   到着日時
         * @param transitDays   所要日数
         */
        public record Schedule(Instant departureTime, Instant arrivalTime, int transitDays) { }

        /**
         * 費用（ADR-008 の概算）。
         *
         * @param estimated 概算費用
         * @param currency  通貨
         */
        public record Cost(BigDecimal estimated, String currency) { }

        /**
         * 選べるかどうか。
         *
         * @param capacityAvailable  空き容量があるか（US09 / IT5）
         * @param deadlineSatisfied  希望期限を満たすか
         * @param selectable         選べるか
         * @param unselectableReason 選べない理由。選べるなら {@code null}
         * @param daysOverDeadline   当初の希望期限を何日超えるか（US28）
         */
        public record Availability(
                boolean capacityAvailable,
                boolean deadlineSatisfied,
                boolean selectable,
                String unselectableReason,
                long daysOverDeadline) { }

        // --- 画面が呼ぶ名前（委譲するアクセサ）---

        /** @return 出発日時 */
        public Instant departureTime() {
            return schedule.departureTime();
        }

        /** @return 到着日時 */
        public Instant arrivalTime() {
            return schedule.arrivalTime();
        }

        /** @return 所要日数 */
        public int transitDays() {
            return schedule.transitDays();
        }

        /** @return 概算費用 */
        public BigDecimal estimatedCost() {
            return cost.estimated();
        }

        /** @return 通貨 */
        public String currency() {
            return cost.currency();
        }

        /** @return 空き容量があるか */
        public boolean capacityAvailable() {
            return availability.capacityAvailable();
        }

        /** @return 希望期限を満たすか */
        public boolean deadlineSatisfied() {
            return availability.deadlineSatisfied();
        }

        /** @return 選べるか */
        public boolean selectable() {
            return availability.selectable();
        }

        /** @return 選べない理由 */
        public String unselectableReason() {
            return availability.unselectableReason();
        }

        /** @return 当初の希望期限を何日超えるか */
        public long daysOverDeadline() {
            return availability.daysOverDeadline();
        }

        /**
         * 当初の希望期限を超えるか（US28）。
         *
         * <p><strong>「期限を過ぎます」だけでは足りない。</strong> 1 日なのか 2 週間なのかで、
         * 荷主への説明も、代替を探すかの判断も変わる。
         */
        public boolean overshootsDeadline() {
            return availability.daysOverDeadline() > 0;
        }
    }
}
