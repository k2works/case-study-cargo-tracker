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
        assertThat(EstimateFilter.Criteria.none().isEmpty()).isTrue();
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
