package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>読み取りのクエリが 1 つ残らず H2 のスモークに載っていること</strong>を確かめる。
 *
 * <p>{@link H2DialectSmokeTest} は「H2 でも解釈できる SQL か」を見るが、
 * <strong>載せるのは人の記憶に預けられていた</strong>。実際、2 イテレーション続けて外した。
 *
 * <ul>
 *   <li>IT17: 訂正一覧（{@code CorrectionQueryService}）—— セルフレビューで気づいた</li>
 *   <li>IT18: 見積一覧（{@code EstimateQueryService}）—— 同じくセルフレビューで気づいた</li>
 * </ul>
 *
 * <p><strong>2 回続けて同じ形で外したなら、それは人ではなく方式の問題である。</strong>
 * 本テストは、クエリサービスを新しく作ってスモークに載せ忘れると赤になる。
 *
 * <p><strong>なぜ「自動で全部叩く」形にしないか。</strong> Bean を集めて読み取りメソッドを
 * 総当たりする案もあったが、引数の要るメソッドには値をこしらえる必要があり、
 * <strong>叩けなかったものを黙って飛ばす</strong>形になりやすい。それは名簿方式に戻るのと
 * 同じである。<strong>叩き方は人が書き、載せ忘れだけを機械が見る。</strong>
 *
 * <p><strong>見るのはメソッド単位である</strong>（IT19 のクローズ前レビュー）。
 * 型の単位で見ていたころは、<strong>既存のサービスにメソッドを 1 つ足しても
 * 検出しなかった</strong>。実測すると {@code findAwaitingTracking} /
 * {@code findInTransit} / {@code findAwaitingNotification} / {@code countMisrouted} の
 * 4 つが、固有の SQL を持ちながら一度も叩かれていなかった ——
 * <strong>次に増えるのは新しいサービスではなくメソッドである</strong>。
 *
 * <p><strong>{@code *QueryService} という名前に頼っている。</strong> 置き場と名前が規約から
 * 外れると、規約を前提にした本テストも外れる（IT6 で {@code TrackingSequence} が
 * {@code infrastructure/acl} にあったために漏れたのと同じ形）。そのため
 * {@link #置き場の規約から外れた読み取りが無い()} で規約自体も固定する。
 */
@DisplayName("H2 のスモークに載せ忘れが無いこと")
class H2DialectCoverageTest {

    /** クエリサービスの置き場（ADR-022 / `architecture_backend.md`）。 */
    private static final String QUERY_SERVICE_PACKAGE = "application/internal/queryservices";

    /** スモークテストの本体。 */
    private static final String SMOKE_TEST = "H2DialectSmokeTest.java";

    /**
     * interface の抽象メソッド宣言（{@code 戻り値 名前(...);}）。
     *
     * <p><strong>インデント 4 の行だけを見る。</strong> 入れ子のレコードや
     * {@code default} メソッドの中の呼び出しまで拾うと、
     * <strong>読み取りの入口でないものを「載せ忘れ」と言い始める</strong>
     * （{@code toList} や {@code of} を拾った）。
     */
    private static final Pattern METHOD = Pattern.compile(
            "(?m)^ {4}(?!static |default |private |public )"
                    + "[\\p{L}\\p{N}_<>\\[\\],?$ ]+?([\\p{L}\\p{N}_$]+)\\s*\\([^)]*\\)\\s*;");

    @Test
    void すべてのクエリサービスがH2のスモークに載っている() {
        String smoke = smokeTestSource();

        List<String> declared = readMethods();
        assertThat(declared)
                .as("走査が空なら、この検査は何も見ていない（IT17 の P1）")
                .isNotEmpty();

        List<String> missing = declared.stream()
                .filter(entry -> !invoked(smoke, entry))
                .toList();

        assertThat(missing)
                .as("H2 のスモークで呼ばれていない読み取り（IT18 の T3）。"
                        + "%s に注入して呼んでください。載せ忘れると、"
                        + "ローカル起動でその画面だけが 500 になります", SMOKE_TEST)
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>名前の重なりで素通りさせない。</strong> 型名を部分文字列で照合していると、
     * {@code ShipperBookingQueryService} を足したときに既存の {@code BookingQueryService} の
     * 宣言と一致し、<strong>新しいほうを載せなくても緑</strong>になる。
     */
    @Test
    void 名前が重なる型を見分けられる() {
        String smoke = """
                @Autowired
                private BookingQueryService bookingQueryService;

                @Test
                void 呼ぶ() {
                    bookingQueryService.search(criteria, page);
                }
                """;

        assertThat(invoked(smoke, "BookingQueryService#search"))
                .as("載せたものは載っていると数えること（IT18 の T3）")
                .isTrue();
        assertThat(invoked(smoke, "ShipperBookingQueryService#search"))
                .as("名前が重なるだけの別の型を、載っていると数えないこと（IT18 の T3）")
                .isFalse();
        assertThat(invoked(smoke, "BookingQueryService#findInTransit"))
                .as("呼んでいないメソッドを、載っていると数えないこと（IT18 の T3）")
                .isFalse();
    }

    /**
     * 読み取りの入口が置き場の規約から外れていないこと。
     *
     * <p><strong>置き場が規約から外れると、規約を前提にした検査も外れる</strong>（IT6）。
     * 上のテストは {@code application/internal/queryservices} の {@code *QueryService} を
     * 数えている。読み取りの入口をそれ以外の場所に作ると、<strong>載せ忘れを検出する検査ごと
     * すり抜ける</strong>。
     */
    @Test
    void 置き場の規約から外れた読み取りが無い() {
        List<String> strays = SourceScan.main().sources().stream()
                .map(source -> source.path().toString())
                .filter(path -> path.endsWith("QueryService.java"))
                .filter(path -> !path.contains(QUERY_SERVICE_PACKAGE))
                // MyBatis の実装は infrastructure/repositories に置く（規約どおり）
                .filter(path -> !path.contains("infrastructure/repositories"))
                .toList();

        assertThat(strays)
                .as("置き場の規約（%s）から外れたクエリサービス（IT18 の T3）。"
                        + "置き場が規約から外れると、規約を前提にした載せ忘れの検査ごと"
                        + "すり抜けます（IT6 の TrackingSequence と同じ形）", QUERY_SERVICE_PACKAGE)
                .isEmpty();
    }

    /** スモークテストの本文（コメントと文字列リテラルを除いたもの）。 */
    private static String smokeTestSource() {
        return SourceScan.of(SourceScan.TEST_ROOT).sources().stream()
                .filter(source -> source.fileName().equals(SMOKE_TEST))
                .map(SourceScan.SourceFile::code)
                .findFirst()
                .orElseThrow(() -> new AssertionError(SMOKE_TEST + " が見つかりません"));
    }

    /**
     * 読み取りの入口を {@code 型名#メソッド名} で集める。
     *
     * <p>インタフェースの宣言だけを見る。<strong>実装のヘルパは対象外である</strong> ——
     * 画面から呼ばれるのは interface の側だけである。
     */
    private static List<String> readMethods() {
        List<String> methods = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            if (!source.path().toString().contains(QUERY_SERVICE_PACKAGE)
                    || !source.fileName().endsWith("QueryService.java")) {
                continue;
            }
            String type = source.fileName().substring(
                    0, source.fileName().length() - ".java".length());
            Matcher declaration = METHOD.matcher(source.code());
            while (declaration.find()) {
                methods.add(type + "#" + declaration.group(1));
            }
        }
        return methods;
    }

    /**
     * その読み取りが、スモークで実際に呼ばれているか。
     *
     * <p>型のフィールド名を取り、{@code 変数名.メソッド名(} が現れることを見る。
     * <strong>注入しただけで呼んでいない</strong>状態を「載せた」と数えない。
     *
     * <p><strong>型名は語の境界で区切る</strong>（クローズ前レビュー）。部分一致だと
     * 接尾辞が重なる別の型を「載っている」と数える。
     */
    private static boolean invoked(String smoke, String entry) {
        String type = entry.substring(0, entry.indexOf('#'));
        String method = entry.substring(entry.indexOf('#') + 1);
        Matcher declaration = Pattern
                .compile("(?<![\\p{L}\\p{N}_$])" + type + "\\s+([\\p{L}\\p{N}_$]+)\\s*;")
                .matcher(smoke);
        while (declaration.find()) {
            if (smoke.contains(declaration.group(1) + "." + method + "(")) {
                return true;
            }
        }
        return false;
    }
}
