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

        /** 期限切れを指す条件。 */
        public static final String EXPIRED = "EXPIRED";

        /** 有効（作成済）を指す条件。 */
        public static final String CREATED = "CREATED";

        /** <strong>状態で絞らない</strong>ことを明示する値（U4。IT20）。 */
        public static final String ALL_STATUSES = "ALL";

        /** 何も絞らない条件。 */
        public static Criteria none() {
            return new Criteria(null, null, null, null, null);
        }

        /**
         * <strong>画面を開いたときの既定</strong>（U4。IT20）。
         *
         * <p>期限切れが混ざったままだと、毎朝の確認でどれがまだ使えるのか分からず
         * <strong>一覧全体が信用されない</strong>。既定では有効だけを出し、
         * 期限切れは {@link #ALL_STATUSES} を選べば取り戻せる。
         */
        public static Criteria defaultView() {
            return new Criteria(null, null, null, null, CREATED);
        }

        /**
         * 画面から届いた値を条件に写す（U4。IT20）。
         *
         * <p><strong>状態が空なら既定（有効のみ）にする。</strong> 「すべて」を見たい人は
         * {@link #ALL_STATUSES} を選ぶ。<strong>空と「すべて」を同じ意味にすると、
         * 既定を有効にした意味が無くなる</strong> —— 一覧を開くたびに期限切れが混ざる。
         */
        public static Criteria fromRequest(
                String origin, String destination,
                LocalDate createdFrom, LocalDate createdTo, String status) {
            return new Criteria(origin, destination, createdFrom, createdTo,
                    blank(status) ? defaultView().status() : status);
        }

        /**
         * <strong>利用者が自分で入れた条件が 1 つも無いか</strong>（H6）。
         *
         * <p><strong>既定の状態は数えない。</strong> 数えると、一覧を開いただけの人に
         * 「条件を変えてお試しください」と言うことになる ——
         * その人は条件を触っていない。
         */
        public boolean 利用者が絞っていない() {
            return blank(origin) && blank(destination)
                    && createdFrom == null && createdTo == null
                    && CREATED.equalsIgnoreCase(status);
        }

        /**
         * 画面が扱いを決めている状態か（H9）。
         *
         * <p><strong>{@code ALL} は仕様である。</strong> これを「知らない値では絞らない」の
         * フォールバックに任せると、{@code ?status=ALL} と {@code ?status=FOO} が
         * 同じ挙動になり、<strong>既定を有効にした意味が別の規則に寄りかかる</strong>。
         */
        public static boolean isKnownStatus(String status) {
            if (blank(status)) {
                return false;
            }
            String requested = status.strip();
            return ALL_STATUSES.equalsIgnoreCase(requested)
                    || CREATED.equalsIgnoreCase(requested)
                    || EXPIRED.equalsIgnoreCase(requested);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * 0 件だったときに、<strong>何をすれば先へ進めるか</strong>を分ける（U5。IT20）。
     *
     * <p>「条件に一致しません」としか出さないと、港コードを打ち間違えた人は
     * <strong>条件を何度変えても 0 件のまま</strong>になる。
     * <strong>気づく手段は、次の行動へ繋がっていなければ仕事が進まない。</strong>
     */
    public enum EmptyReason {

        /** まだ 1 件も作られていない。作りに行くのが次の行動である。 */
        NONE_AT_ALL,

        /**
         * <strong>既定の「有効」で隠れているだけ</strong>（H6）。
         *
         * <p>手持ちが全て期限切れの朝は、条件を 1 つも入れていないのに 0 件になる。
         * ここで「条件を変えてお試しください」と言うと、
         * <strong>触っていない条件を直せと言うことになる。</strong>
         */
        HIDDEN_BY_DEFAULT,

        /** 指定した港コードが港マスタに無い。<strong>打ち間違いを疑う。</strong> */
        UNKNOWN_PORT,

        /** 作成日の「から」が「まで」より後。<strong>何を入れても 0 件になる。</strong> */
        REVERSED_DATE_RANGE,

        /** 条件は妥当だが、合う見積が無い。条件を広げるのが次の行動である。 */
        NO_MATCH
    }

    /**
     * 0 件の理由を決める（U5。IT20）。
     *
     * <p><strong>「まだ 1 件も無い」が最優先である。</strong> 1 件も無い人に
     * 「港コードが実在しません」と言っても直しようがない。
     *
     * <p><strong>港の実在は問い合わせだが、どれを先に伝えるかは規則である</strong>
     * （ADR-022）。問い合わせの結果を引数で受け取り、判断だけをここに置く。
     *
     * @param all           絞り込む前のすべての見積
     * @param criteria      指定された条件
     * @param unknownPorts  条件に指定された港のうち、港マスタに無かったもの
     */
    public static EmptyReason emptyReason(
            List<EstimateSummaryView> all, Criteria criteria, List<String> unknownPorts) {
        if (all.isEmpty()) {
            return EmptyReason.NONE_AT_ALL;
        }
        // **既定が隠しただけなら、そう言う**（H6）。利用者は条件を触っていない
        if (criteria.利用者が絞っていない()) {
            return EmptyReason.HIDDEN_BY_DEFAULT;
        }
        if (!unknownPorts.isEmpty()) {
            return EmptyReason.UNKNOWN_PORT;
        }
        if (criteria.createdFrom() != null && criteria.createdTo() != null
                && criteria.createdFrom().isAfter(criteria.createdTo())) {
            return EmptyReason.REVERSED_DATE_RANGE;
        }
        return EmptyReason.NO_MATCH;
    }

    /**
     * 条件に指定された港コード（実在を確かめる相手）。
     *
     * <p><strong>空欄は確かめない。</strong> 指定していない条件は打ち間違えようがない。
     */
    public static List<String> requestedPorts(Criteria criteria) {
        return java.util.stream.Stream.of(criteria.origin(), criteria.destination())
                .filter(code -> !Criteria.blank(code))
                .map(String::strip)
                .map(code -> code.toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
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
     *
     * <p><strong>知らない値では絞らない</strong>（IT19 のクローズ前レビュー）。以前は
     * 「{@code EXPIRED} でなければ有効で絞る」と書いており、
     * <strong>打ち間違いが黙って別の絞り込みに化けていた</strong>。
     * 分からない条件で結果を狭めるより、狭めないほうが安全である。
     */
    private static boolean matchesStatus(EstimateSummaryView estimate, String status) {
        if (Criteria.blank(status)) {
            return true;
        }
        String requested = status.strip();
        // **すべては仕様である**（H9）。「知らない値では絞らない」に任せない
        if (Criteria.ALL_STATUSES.equalsIgnoreCase(requested)) {
            return true;
        }
        if (Criteria.EXPIRED.equalsIgnoreCase(requested)) {
            return estimate.status().expired();
        }
        if (Criteria.CREATED.equalsIgnoreCase(requested)) {
            return !estimate.status().expired();
        }
        return true;
    }
}
