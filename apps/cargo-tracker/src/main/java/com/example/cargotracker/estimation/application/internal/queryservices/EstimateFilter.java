package com.example.cargotracker.estimation.application.internal.queryservices;

import java.time.LocalDate;
import java.util.List;

/**
 * 見積一覧の絞り込み（{@code ui_design.md}「見積一覧」。IT19 の C4）。
 *
 * <p><strong>これは規則であって問い合わせではない</strong>（ADR-022）。とくに状態の扱いが
 * そうである —— {@code estimate.status} 列は作成時に書き込まれたまま更新されず、
 * 読み取りは希望期限と業務日から導出している（ADR-019 と同じ考え方）。
 * <strong>SQL で {@code WHERE status = 'EXPIRED'} と書くと、いつでも 0 件になる。</strong>
 *
 * <p>そのため状態の絞り込みは<strong>導出した状態に対して</strong>行う。
 *
 * <p><strong>一覧は「毎朝どう使うか」から確かめる。</strong> 期限切れが混ざったままだと、
 * どれがまだ使えるのか分からず一覧全体が信用されない。
 */
public final class EstimateFilter {

    private EstimateFilter() {
    }

    /**
     * 絞り込みの条件。
     *
     * <p><strong>すべて任意である。</strong> 指定しなかった条件は絞らない。
     *
     * @param origin      出発地 UN/LOCODE
     * @param destination 目的地 UN/LOCODE
     * @param createdFrom 作成日の下限（この日を含む）
     * @param createdTo   作成日の上限（この日を含む）
     * @param status      状態（{@code CREATED} / {@code EXPIRED}）
     */
    public record Criteria(
            String origin,
            String destination,
            LocalDate createdFrom,
            LocalDate createdTo,
            String status) {

        /** 何も絞らない条件。 */
        public static Criteria none() {
            return new Criteria(null, null, null, null, null);
        }

        /** 1 つでも条件が指定されているか。<strong>0 件のときの文言を分けるために使う。</strong> */
        public boolean isEmpty() {
            return blank(origin) && blank(destination)
                    && createdFrom == null && createdTo == null && blank(status);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    /** 条件に合う見積だけを残す。 */
    public static List<EstimateSummaryView> apply(
            List<EstimateSummaryView> estimates, Criteria criteria) {
        return estimates.stream()
                .filter(estimate -> matches(estimate, criteria))
                .toList();
    }

    private static boolean matches(EstimateSummaryView estimate, Criteria criteria) {
        return matchesLocation(criteria.origin(), estimate.route().origin())
                && matchesLocation(criteria.destination(), estimate.route().destination())
                && notBefore(estimate.createdOn(), criteria.createdFrom())
                && notAfter(estimate.createdOn(), criteria.createdTo())
                && matchesStatus(estimate, criteria.status());
    }

    /** <strong>大文字小文字を問わない。</strong> UN/LOCODE を手で打つ人がいる。 */
    private static boolean matchesLocation(String required, String actual) {
        return Criteria.blank(required) || required.strip().equalsIgnoreCase(actual);
    }

    private static boolean notBefore(LocalDate createdOn, LocalDate from) {
        return from == null || createdOn == null || !createdOn.isBefore(from);
    }

    private static boolean notAfter(LocalDate createdOn, LocalDate to) {
        return to == null || createdOn == null || !createdOn.isAfter(to);
    }

    /**
     * 状態で絞る。
     *
     * <p><strong>導出した状態を見る。</strong> 保存された列は更新されないため使えない。
     */
    private static boolean matchesStatus(EstimateSummaryView estimate, String status) {
        if (Criteria.blank(status)) {
            return true;
        }
        boolean wantExpired = "EXPIRED".equalsIgnoreCase(status.strip());
        return estimate.status().expired() == wantExpired;
    }
}
