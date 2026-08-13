package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>一覧を返すクエリサービスには問い合わせ回数の計測がある</strong>（IT15 の Try T3）。
 *
 * <p>T3 は「一覧を返すクエリサービスを書いたら<strong>その場で</strong>問い合わせ回数の
 * テストを書く（後から足すのではなく、一覧と同時に）」と定めている。
 *
 * <p><strong>本イテレーションの実装がその規律を破った。</strong> C1 で
 * {@code HandlingQueryService.findPendingDischarges} を足しながら計測を書かず、
 * <strong>途中レビューで初めて気づいた</strong>（IT16 レビュー H1）。
 * IT15 の P3（C4 と同じ型を、C4 を返済した同じイテレーションで作った）と同じ形である。
 *
 * <p><strong>返済したことは、次に書くときに思い出す保証にならない。</strong>
 * 規律を人の記憶に預けず、構造で止める（ADR-021 と同じ判断）。
 *
 * <p><strong>対象はコードから導く。</strong> 名簿にすると、名簿に書き忘れたものが漏れる
 * （{@code MapperTableOwnershipTest} で実際に 3 イテレーション漏れた）。
 * {@code *QueryService} のインターフェースを走査し、{@code List<} を返すメソッドを持つ
 * ものを対象とする。
 */
@DisplayName("一覧を返すクエリサービスには問い合わせ回数の計測がある（T3）")
class ListQueryMeasurementTest {


    /**
     * <strong>計測がまだ無いクエリサービス</strong>（IT16 時点の負債）。
     *
     * <p><strong>黙って通すのではなく、名前で残す</strong>（ADR-015 の {@code ALLOWED} と
     * 同じ形）。ここに行を足すのは<strong>新しい一覧を計測せずに書いた</strong>ことを
     * 意味する — <strong>足す前に、まず計測を書けないかを問う。</strong>
     *
     * <p><strong>この表は縮む一方であるべきものである。</strong> IT16 の時点で 6 件あり、
     * <strong>IT17 の R5 ですべて返した</strong>。返す過程で
     * <strong>訂正の承認待ち一覧に本物の N+1 が見つかった</strong>
     * （1 行ごとに荷役を開いていた。まとめて引く形に直した）。
     *
     * <p><strong>据え置きの表は空である。</strong> 行を足すと
     * {@link #据え置きの表は空である()} が落ちる。
     */
    private static final Map<String, String> NOT_MEASURED_YET = new LinkedHashMap<>();

    /**
     * <strong>据え置きの表が空であることを保つ</strong>（IT17 の R5）。
     *
     * <p>返し終えた表は、<strong>次に一覧を書いた人が行を足す場所</strong>になりやすい。
     * 空であることを検査が言い続ければ、足す変更は必ず目に入る。
     *
     * <p><strong>この検査は IT17 の品質ゲートで足した。</strong> R5 のコミットで
     * 「空であることを検査で固定した」と書きながら、<strong>入っていたのは
     * Javadoc の参照だけだった</strong>。書いたつもりのものは、実行されるまで
     * 書いたことにならない。
     */
    @Test
    void 据え置きの表は空である() {
        assertThat(NOT_MEASURED_YET)
                .as("""
                        計測の据え置きに行が足されています（IT17 の R5 で空にしました）。

                        **一覧は、書いたときには速く、運用で遅くなります。**
                        計測を先に書けない理由があるなら、この検査を消す判断ごと
                        レビューに出してください。""")
                .isEmpty();
    }

    /**
     * <strong>一覧を返すクエリサービスは、問い合わせ回数を計測している。</strong>
     *
     * <p>違反があればサービス名を並べて落とす。
     */
    @Test
    void 一覧を返すクエリサービスには計測がある() {
        Set<String> listServices = listReturningQueryServices();
        assertThat(listServices)
                .as("一覧を返すクエリサービスが 1 つも見つからないなら、検査は何も見ていない（IT16 の T3）")
                .isNotEmpty();

        Set<String> measured = measuredServices();
        Set<String> missing = new TreeSet<>(listServices);
        missing.removeAll(measured);
        missing.removeAll(NOT_MEASURED_YET.keySet());

        assertThat(missing)
                .as("""
                        一覧を返すクエリサービスに問い合わせ回数のテストがありません（T3）。

                        **待ち行列が伸びるほど遅くなる形は、時間では判別できません。**
                        QueryCounter を使い、件数を変えて増え方を見てください
                        （1 件のときと 5 件のときで回数が変わらないこと）。

                        由来: IT16 の T3""")
                .isEmpty();
    }

    /**
     * <strong>据え置きの表に、もう解消したものを残さない。</strong>
     *
     * <p>返したのに名前が残っていると、<strong>表が縮んでいないように見える</strong>。
     * 縮まない表は、いずれ読まれなくなる。
     */
    @Test
    void 据え置きの表に解消済みのものを残さない() {
        Set<String> stale = new TreeSet<>(NOT_MEASURED_YET.keySet());
        stale.retainAll(measuredServices());

        assertThat(stale)
                .as("計測を書いたクエリサービスが据え置きの表に残っています。表から消してください（IT16 の T3）")
                .isEmpty();
    }

    /**
     * <strong>据え置きの表に、存在しないサービスを残さない。</strong>
     *
     * <p>名簿方式は<strong>実体を失った行に気づけない</strong>
     * （{@code CrossContextPortPolicyTest} が同じ検査を持つ）。
     */
    @Test
    void 据え置きの表に実在しないサービスを残さない() {
        Set<String> phantom = new TreeSet<>(NOT_MEASURED_YET.keySet());
        phantom.removeAll(listReturningQueryServices());

        assertThat(phantom)
                .as("据え置きの表に実在しないクエリサービスがあります（IT16 の T3）")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>本検査だけメタテストが無かった</strong>（クローズ前レビューの H1）。
     * 他の 6 本には付けており、<strong>「検査が働くこと」を確かめる規律を
     * 説く検査自身が、それを欠いていた</strong>。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。
     */
    @Test
    void 実コードの形のクエリサービスを見分けられる() {
        Set<String> services = listReturningQueryServices();

        assertThat(services)
                .as("インターフェースとして書かれたものを拾えること")
                .contains("HandlingQueryService", "BillingQueryService")
                // **形で絞ると形が違うものが漏れる。** クラスとして書かれたものも拾う
                .contains("BookingNotificationQueryService", "RouteProposalQueryService");

        assertThat(services)
                .as("実装（MyBatis*）を対象にしないこと（対象は宣言の側である）")
                .noneSatisfy(s -> assertThat(s).startsWith("MyBatis"));

        assertThat(measuredServices())
                .as("計測済みを 1 つも拾えないなら、検査は何も見ていない（IT16 の T3）")
                .isNotEmpty();
    }

    /** {@code List<} を返すメソッドを持つ {@code *QueryService} インターフェース。 */
    private static Set<String> listReturningQueryServices() {
        Set<String> names = new LinkedHashSet<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            String fileName = source.fileName();
            if (!fileName.endsWith("QueryService.java") || fileName.startsWith("MyBatis")) {
                continue;
            }
            // **インターフェースとは限らない。** 実装を持たないクラスとして
            // 書かれているものもある（BookingNotificationQueryService / RouteProposalQueryService）。
            // **形で絞ると、形が違うものが漏れる。**
            if (source.code().contains("List<")) {
                names.add(fileName.substring(0, fileName.length() - ".java".length()));
            }
        }
        return names;
    }

    /** {@code QueryCounter} と一緒に名前が現れるクエリサービス。 */
    private static Set<String> measuredServices() {
        Set<String> measured = new LinkedHashSet<>();
        Set<String> services = listReturningQueryServices();
        // **検査自身を「計測済み」と数えない。** 据え置きの表に名前を持つため、
        // 除外しないと**すべてが計測済みに見える**（自己参照）。
        // SourceScan は呼び出し元を既定で外すため、ここに書くことはもう無い（R8）
        for (SourceScan.SourceFile source : SourceScan.test().sources()) {
            String text = source.text();
            if (!text.contains("QueryCounter")) {
                continue;
            }
            for (String service : services) {
                if (text.contains(service)) {
                    measured.add(service);
                }
            }
        }
        return measured;
    }
}
