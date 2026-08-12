package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 単純な委譲アクセサを<strong>増やさない</strong>（IT17 の引き継ぎ C1）。
 *
 * <p>IT17 でレコードを 20 件分割したとき、テンプレートを触らずに済ませるため
 * <strong>委譲するだけのアクセサを大量に足した</strong>。分割の効能は入れ子側で
 * 完結しており、<strong>この層はテンプレート互換のためだけに存在する</strong>。
 *
 * <p><strong>これは育つ負債である。</strong> レコードを分けるたびに増える。
 * IT18 で数え直したところ、記録の「6 レコード・34 個」に対し
 * <strong>実測は 38 ファイル・249 個</strong>だった（4 度目の過小記録）。
 *
 * <p>IT18 で {@code BookingView} の 39 個を返した。残りは<strong>上限で固定する</strong> ——
 * 減らすのは自由だが、<strong>増やすと落ちる</strong>。
 *
 * <p><strong>述語は対象外である。</strong> {@code isRouted()} のような判断は
 * ビューが持つべきであり、テンプレートに書き下すと同じ規則が画面の数だけ散る。
 * ここで数えるのは<strong>戻り値をそのまま返すだけ</strong>のものに限る。
 */
@DisplayName("委譲アクセサを増やさない（C1）")
class DelegatingAccessorRatchetTest {

    /**
     * 上限（IT18 時点の実測）。
     *
     * <p><strong>この数は下げる一方であるべきものである。</strong> 上げるときは、
     * なぜ委譲の層を増やすのかを説明できなければならない。
     */
    private static final int LIMIT = 210;

    /** {@code public T name() { return field.other(); }} の形。 */
    private static final Pattern DELEGATION = Pattern.compile(
            "\\n    public [\\w<>,.\\[\\] ]+ \\w+\\(\\) \\{\\n        return \\w+\\.\\w+\\(\\);\\n    \\}");

    /**
     * <strong>委譲アクセサが上限を超えない。</strong>
     *
     * <p>超えたら、足したぶんを畳むか、なぜ増やすのかを説明して上限を上げる。
     */
    @Test
    void 委譲アクセサは上限を超えない() {
        Map<String, Integer> byFile = countByFile();
        int total = byFile.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(total)
                .as("""
                        単純な委譲アクセサが増えています（IT17 の C1）。

                        **レコードを分けるたびに増える育つ負債です。**
                        分割の効能は入れ子側で完結しており、この層はテンプレート互換の
                        ためだけに存在します。足したぶんを畳むか、なぜ増やすのかを
                        説明したうえで上限を上げてください。

                        いま数えた内訳（多い順）:
                        """ + 内訳(byFile) + """

                        由来: IT17 の C1""")
                .isLessThanOrEqualTo(LIMIT);
    }

    /**
     * ファイル別の数（多い順）。
     *
     * <p><strong>どこで増えたかが分からないと、直しようがない</strong>（IT18 の C2）。
     * 以前は合計しか出しておらず、赤くなった人は<strong>全ファイルを自分で数え直す</strong>
     * ことになっていた。
     */
    private static String 内訳(Map<String, Integer> byFile) {
        return byFile.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    /** 委譲アクセサを持つファイルと、その数。 */
    private static Map<String, Integer> countByFile() {
        Map<String, Integer> byFile = new java.util.LinkedHashMap<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            int count = count(source.code());
            if (count > 0) {
                byFile.put(source.fileName(), count);
            }
        }
        return byFile;
    }

    /**
     * <strong>上限が実態から離れすぎていない。</strong>
     *
     * <p>返したのに上限を下げないと、<strong>次に増やしても気づけない</strong>。
     * ラチェットは締めてこそ働く。
     */
    @Test
    void 上限は実態から離れすぎていない() {
        int total = countByFile().values().stream().mapToInt(Integer::intValue).sum();

        assertThat(LIMIT - total)
                .as("""
                        委譲アクセサを返したのに上限が下がっていません。

                        **緩んだラチェットは、次に増えたことを教えてくれません。**
                        上限を実態に合わせてください。

                        由来: IT17 の C1""")
                .isLessThanOrEqualTo(10);
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 述語を巻き込まないことも見る。
     */
    @Test
    void 委譲と述語を見分けられる() {
        String delegation = """

                    public String origin() {
                        return delivery.origin();
                    }
                """;
        String predicate = """

                    public boolean isRouted() {
                        return !delivery.itinerary().isEmpty();
                    }
                """;

        assertThat(count(delegation)).as("委譲を数えること（IT17 の C1）").isEqualTo(1);
        assertThat(count(predicate))
                .as("述語を数えないこと（常に落ちる検査で緑にしない）（IT17 の C1）")
                .isZero();
    }

    private static int count(String source) {
        int found = 0;
        Matcher matcher = DELEGATION.matcher(source);
        while (matcher.find()) {
            found++;
        }
        return found;
    }
}
