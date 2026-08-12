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

    @Test
    void すべてのクエリサービスがH2のスモークに載っている() {
        String smoke = SourceScan.of(SourceScan.TEST_ROOT).sources().stream()
                .filter(source -> source.path().getFileName().toString().equals(SMOKE_TEST))
                .map(SourceScan.SourceFile::code)
                .findFirst()
                .orElseThrow(() -> new AssertionError(SMOKE_TEST + " が見つかりません"));

        List<String> missing = new ArrayList<>();
        for (String service : queryServices()) {
            // **型を書いただけでは載せたことにならない。** 注入して呼ぶところまでを見る
            if (!smoke.contains(service) || !invoked(smoke, service)) {
                missing.add(service);
            }
        }

        assertThat(missing)
                .as("H2 のスモークで呼ばれていないクエリサービス（IT18 の T3）。"
                        + "%s に注入して呼んでください。載せ忘れると、"
                        + "ローカル起動でその画面だけが 500 になります", SMOKE_TEST)
                .isEmpty();
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

    /** 読み取りの入口（インタフェース）の名前を集める。 */
    private static List<String> queryServices() {
        return SourceScan.main().sources().stream()
                .filter(source -> source.path().toString().contains(QUERY_SERVICE_PACKAGE))
                .map(source -> source.path().getFileName().toString())
                .filter(name -> name.endsWith("QueryService.java"))
                .map(name -> name.substring(0, name.length() - ".java".length()))
                .sorted()
                .toList();
    }

    /**
     * その型のフィールドが、実際に呼ばれているか。
     *
     * <p>フィールド宣言から変数名を取り、{@code 変数名.} が本文に現れることを見る。
     * <strong>注入しただけで呼んでいない</strong>状態を「載せた」と数えない。
     */
    private static boolean invoked(String smoke, String service) {
        Matcher declaration = Pattern
                .compile(service + "\\s+([\\p{L}\\p{N}_$]+)\\s*;")
                .matcher(smoke);
        while (declaration.find()) {
            if (smoke.contains(declaration.group(1) + ".")) {
                return true;
            }
        }
        return false;
    }
}
