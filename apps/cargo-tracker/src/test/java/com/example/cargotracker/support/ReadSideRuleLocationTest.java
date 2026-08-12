package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>読み取り側の「規則」を infrastructure に書かない</strong>（ADR-022）。
 *
 * <p>ADR-022 は「読み取り側の<strong>規則</strong>は application 層に置き、
 * <strong>問い合わせ</strong>は infrastructure に残す」と決めている。
 * <strong>決めただけで、守っているかを見るものが無かった</strong> ——
 * ADR-009 が 7 イテレーションのあいだ半分しか守られず、違反が 5 本増えていたのと同じ形である。
 *
 * <p><strong>見るのは「しきい値の比較」だけである。</strong> 規則かどうかを機械が
 * 一般に判定することはできない。だが<strong>数と比べて分岐する</strong>コードは、
 * ほぼ必ず業務か画面の規則である。
 *
 * <ul>
 *   <li>「残り 3 日以内は赤、7 日以内は橙」（{@code ui_design.md}）—— 規則</li>
 *   <li>「行が無ければ空を返す」（{@code == null} / {@code isEmpty()}）—— 問い合わせの後始末</li>
 * </ul>
 *
 * <p><strong>定数に逃がしても拾う</strong>（IT19 のクローズ前レビュー）。当初は数値リテラルだけを
 * 見ており、<strong>{@code days <= CRITICAL_DAYS} と書けば素通りした</strong> ——
 * まさに移設先の {@code DeadlineUrgency} がその形である。
 *
 * <p><strong>狭いことを承知で狭くしている。</strong> 広げると、行の有無を見るだけの
 * 分岐まで赤くなり、<strong>抑止の注釈が並んで検査の意味が薄れる</strong>。
 * ここで止めたいのは「しきい値が 2 か所に散る」ことであり、それは実際に起きていた
 * （{@code urgencyClass} の 3 日 / 7 日）。
 */
@DisplayName("読み取り側の規則は application 層にある（ADR-022）")
class ReadSideRuleLocationTest {

    /** 問い合わせの置き場。 */
    private static final String INFRASTRUCTURE = "infrastructure/repositories";

    /**
     * しきい値との比較。
     *
     * <p>変数どうしの比較（{@code a < b}）ではなく、<strong>数と比べている</strong>ものを拾う。
     */
    private static final Pattern THRESHOLD = Pattern.compile(
            "(?m)^.*\\b(?:if|return)\\b[^;\\n]*[\\p{L}\\p{N}_$)]\\s*(?:<=|>=|<|>)\\s*"
                    + "(?:-?\\d+|[A-Z][A-Z0-9_]{2,})\\b.*$");

    @Test
    void 問い合わせ側にしきい値の分岐が無い() {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            if (!source.path().toString().contains(INFRASTRUCTURE)
                    || !source.fileName().endsWith("QueryService.java")) {
                continue;
            }
            scanned++;
            Matcher matcher = THRESHOLD.matcher(source.code());
            while (matcher.find()) {
                violations.add("%s: %s".formatted(source.fileName(), matcher.group().strip()));
            }
        }

        assertThat(scanned)
                .as("走査が空なら、この検査は何も見ていない（IT17 の P1）")
                .isPositive();
        assertThat(violations)
                .as("""
                        問い合わせ側にしきい値の分岐があります（ADR-022）。

                        **規則は application 層に置いてください。**
                        infrastructure に書くと、同じしきい値が画面ごとに散り、
                        規則を壊すテストを書く場所も無くなります。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p>「最小の違反例」だけで確かめると、<strong>実コードの形の違反を見逃したまま
     * 緑になる</strong>。実際にあった {@code urgencyClass} の形で確かめる。
     */
    @Test
    void 実コードの形の違反を検出できる() {
        String actual = """
                private static String urgencyClass(long daysUntilDeadline) {
                    if (daysUntilDeadline <= 3) {
                        return "text-danger fw-bold";
                    }
                    return "";
                }
                """;
        assertThat(THRESHOLD.matcher(actual).find())
                .as("実際にあった形（残り日数のしきい値。ADR-022）を検出できること")
                .isTrue();
    }

    /** <strong>行の有無を見る分岐は規則ではない。</strong> 赤くしない。 */
    @Test
    void 問い合わせの後始末は違反にしない() {
        String queryHousekeeping = """
                if (row == null) {
                    return Optional.empty();
                }
                if (bookingIds.isEmpty()) {
                    return List.of();
                }
                return rows.stream().filter(r -> r.getId() != exceptionId).toList();
                """;
        assertThat(THRESHOLD.matcher(queryHousekeeping).find())
                .as("行の有無を見るだけの分岐を違反にしないこと（ADR-022）")
                .isFalse();
    }
}
