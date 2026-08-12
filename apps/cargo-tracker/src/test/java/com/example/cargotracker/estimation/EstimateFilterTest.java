package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.estimation.application.internal.queryservices.EstimateFilter;
import com.example.cargotracker.estimation.application.internal.queryservices.EstimateSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 見積一覧の絞り込みの規則（ADR-022。IT19 の C4）。
 *
 * <p><strong>規則を application 層へ出したから、こう書ける。</strong> infrastructure に
 * 埋まっていたら、境界を確かめるのに毎回 SQL を実行することになる
 * （クローズ前レビュー: 絞り込みの検証が統合テスト 6 本だけだった）。
 */
@DisplayName("見積一覧の絞り込みの規則（C4）")
class EstimateFilterTest {

    private static final LocalDate 作成日 = LocalDate.of(2026, java.time.Month.AUGUST, 12);

    private static EstimateSummaryView 見積(String origin, String destination, boolean expired) {
        return new EstimateSummaryView(
                "EST-" + origin + destination,
                new EstimateSummaryView.Route(origin, destination),
                new EstimateSummaryView.Cargo("GENERAL", "一般貨物", new BigDecimal("1000")),
                作成日.plusDays(30),
                new BigDecimal("120000"),
                new EstimateSummaryView.Status(
                        expired ? "期限切れ" : "有効", "bg-secondary", expired),
                作成日);
    }

    private static final EstimateSummaryView 有効 = 見積("JPOSA", "USLAX", false);
    private static final EstimateSummaryView 期限切れ = 見積("JPYOK", "DEHAM", true);
    private static final List<EstimateSummaryView> すべて = List.of(有効, 期限切れ);

    private static List<EstimateSummaryView> 絞る(EstimateFilter.Criteria criteria) {
        return EstimateFilter.apply(すべて, criteria);
    }

    private static EstimateFilter.Criteria 条件(
            String origin, LocalDate from, LocalDate to, String status) {
        return new EstimateFilter.Criteria(origin, null, from, to, status);
    }

    @Test
    void 条件が無ければ絞らない() {
        assertThat(絞る(EstimateFilter.Criteria.none())).isEqualTo(すべて);
    }

    /**
     * <strong>既定は「有効」である</strong>（U4）。
     *
     * <p>期限切れが混ざったままだと、毎朝の確認でどれがまだ使えるのか分からず、
     * <strong>一覧全体が信用されない</strong>。状態を指定せずに開いたときは有効だけを出す。
     */
    @Test
    void 状態を指定せずに開くと有効だけが出る() {
        assertThat(絞る(EstimateFilter.Criteria.none())).containsExactly(有効, 期限切れ);
        assertThat(絞る(EstimateFilter.Criteria.defaultView())).containsExactly(有効);
    }

    /**
     * <strong>「すべて」は明示して選ぶ</strong>（U4）。
     *
     * <p>既定で隠した期限切れを、利用者が取り戻せる手段が要る。
     */
    @Test
    void すべてを明示すれば期限切れも出る() {
        assertThat(絞る(条件(null, null, null, EstimateFilter.Criteria.ALL_STATUSES)))
                .isEqualTo(すべて);
    }

    /**
     * <strong>0 件の理由を分ける</strong>（U5）。
     *
     * <p>「条件に一致しません」としか出さないと、港コードを打ち間違えた人は
     * <strong>条件を何度変えても 0 件のまま</strong>になる。
     */
    @Test
    void まだ一件も無いことと条件に一致しないことを区別する() {
        assertThat(EstimateFilter.emptyReason(
                        List.of(), EstimateFilter.Criteria.defaultView(), List.of()))
                .isEqualTo(EstimateFilter.EmptyReason.NONE_AT_ALL);

        assertThat(EstimateFilter.emptyReason(
                        すべて, 条件("JPOSA", null, null, "EXPIRED"), List.of()))
                .isEqualTo(EstimateFilter.EmptyReason.NO_MATCH);
    }

    /**
     * <strong>既定の絞り込みが原因の 0 件を、条件の不一致と混同しない</strong>
     * （クローズ前レビュー H6）。
     *
     * <p>手持ちが全て期限切れの朝は、<strong>条件を 1 つも入れていないのに</strong>
     * 「条件を変えてお試しください」が出る。
     * <strong>U5 が解こうとした形を、U4 が新しく作っていた。</strong>
     */
    @Test
    void 既定で隠れただけの零件はそう伝える() {
        assertThat(EstimateFilter.emptyReason(
                        List.of(期限切れ), EstimateFilter.Criteria.defaultView(), List.of()))
                .isEqualTo(EstimateFilter.EmptyReason.HIDDEN_BY_DEFAULT);

        assertThat(EstimateFilter.emptyReason(
                        List.of(期限切れ), 条件("JPOSA", null, null, null), List.of()))
                .as("**利用者が条件を入れていれば、それは条件の不一致である**")
                .isEqualTo(EstimateFilter.EmptyReason.NO_MATCH);

        assertThat(EstimateFilter.emptyReason(
                        List.of(期限切れ), 条件(null, null, null,
                                EstimateFilter.Criteria.ALL_STATUSES), List.of()))
                .as("**すべてを選んで 0 件なら、隠れているのではない**")
                .isEqualTo(EstimateFilter.EmptyReason.NO_MATCH);
    }

    /**
     * <strong>「すべて」は仕様として実装されている</strong>（クローズ前レビュー H9）。
     *
     * <p>初版は {@code ALL} の分岐が無く、「知らない値では絞らない」という
     * <strong>別の規則のフォールバックに偶然乗っていた</strong> ——
     * {@code ?status=ALL} と {@code ?status=FOO} が同じ挙動だった。
     */
    @Test
    void すべてと知らない値は別の扱いである() {
        assertThat(EstimateFilter.Criteria.isKnownStatus(
                        EstimateFilter.Criteria.ALL_STATUSES)).isTrue();
        assertThat(EstimateFilter.Criteria.isKnownStatus("CREATED")).isTrue();
        assertThat(EstimateFilter.Criteria.isKnownStatus("EXPIRED")).isTrue();
        assertThat(EstimateFilter.Criteria.isKnownStatus("FOO"))
                .as("**打ち間違いは「すべて」ではない**")
                .isFalse();
    }

    /** <strong>状態が空なら既定（有効のみ）にする</strong>（U4。本番の経路で確かめる）。 */
    @Test
    void 状態が空なら既定になる() {
        assertThat(EstimateFilter.Criteria.fromRequest(null, null, null, null, null).status())
                .isEqualTo(EstimateFilter.Criteria.CREATED);
        assertThat(EstimateFilter.Criteria.fromRequest(null, null, null, null, "  ").status())
                .isEqualTo(EstimateFilter.Criteria.CREATED);
        assertThat(EstimateFilter.Criteria.fromRequest(null, null, null, null, "EXPIRED").status())
                .isEqualTo("EXPIRED");
    }

    /** <strong>実在しない港コードは、条件の不一致とは別である</strong>（U5 の (c)）。 */
    @Test
    void 実在しない港コードを理由として示す() {
        assertThat(EstimateFilter.emptyReason(
                        すべて, 条件("XXXXX", null, null, null), List.of("XXXXX")))
                .isEqualTo(EstimateFilter.EmptyReason.UNKNOWN_PORT);
    }

    /** <strong>作成日の範囲が逆なら、何を入れても 0 件になる</strong>（U5 の (d)）。 */
    @Test
    void 作成日の範囲が逆であることを理由として示す() {
        assertThat(EstimateFilter.emptyReason(
                        すべて, 条件(null, 作成日.plusDays(1), 作成日, null), List.of()))
                .isEqualTo(EstimateFilter.EmptyReason.REVERSED_DATE_RANGE);
    }

    /**
     * <strong>まだ 1 件も無いことが最優先である。</strong>
     *
     * <p>1 件も無い人に「港コードが実在しません」と言っても、直しようがない。
     */
    @Test
    void 一件も無いときは他の理由より先に伝える() {
        assertThat(EstimateFilter.emptyReason(
                        List.of(), 条件("XXXXX", 作成日.plusDays(1), 作成日, null),
                        List.of("XXXXX")))
                .isEqualTo(EstimateFilter.EmptyReason.NONE_AT_ALL);
    }

    /** <strong>UN/LOCODE を手で打つ人がいる。</strong> */
    @Test
    void 港は大文字小文字と前後の空白を問わない() {
        assertThat(絞る(条件("jposa", null, null, null))).containsExactly(有効);
        assertThat(絞る(条件("  JPOSA  ", null, null, null))).containsExactly(有効);
    }

    /** <strong>境界の日はどちらも含む。</strong> 範囲の端を落とすと、その日の分が消える。 */
    @Test
    void 作成日の範囲は両端を含む() {
        assertThat(絞る(条件(null, 作成日, null, null)))
                .as("下限が作成日ちょうど")
                .isEqualTo(すべて);
        assertThat(絞る(条件(null, null, 作成日, null)))
                .as("上限が作成日ちょうど")
                .isEqualTo(すべて);
        assertThat(絞る(条件(null, 作成日.plusDays(1), null, null))).isEmpty();
        assertThat(絞る(条件(null, null, 作成日.minusDays(1), null))).isEmpty();
    }

    @Test
    void 状態で絞れる() {
        assertThat(絞る(条件(null, null, null, "EXPIRED"))).containsExactly(期限切れ);
        assertThat(絞る(条件(null, null, null, "created"))).containsExactly(有効);
    }

    /**
     * <strong>知らない状態は絞らない。</strong>
     *
     * <p>以前は {@code "EXPIRED"} でなければ「有効で絞る」に落ちていた ——
     * <strong>打ち間違いが黙って別の結果になる</strong>（クローズ前レビュー）。
     * 分からない条件で結果を狭めるより、狭めないほうが安全である。
     */
    @Test
    void 知らない状態では絞らない() {
        assertThat(絞る(条件(null, null, null, "FOO")))
                .as("**打ち間違いを別の絞り込みに化けさせない**")
                .isEqualTo(すべて);
    }
}
