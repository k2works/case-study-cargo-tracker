package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
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

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TEST = Path.of("src/test/java");

    /**
     * <strong>計測がまだ無いクエリサービス</strong>（IT16 時点の負債）。
     *
     * <p><strong>黙って通すのではなく、名前で残す</strong>（ADR-015 の {@code ALLOWED} と
     * 同じ形）。ここに行を足すのは<strong>新しい一覧を計測せずに書いた</strong>ことを
     * 意味する — <strong>足す前に、まず計測を書けないかを問う。</strong>
     *
     * <p><strong>この表は縮む一方であるべきものである。</strong> IT16 の時点で 6 件ある。
     * いずれも IT16 より前に書かれたもので、T3 が定まる前の負債である。
     * <strong>IT17 で返す</strong>（`iteration_plan-16.md` の R5）。
     */
    private static final Map<String, String> NOT_MEASURED_YET = new LinkedHashMap<>();

    static {
        NOT_MEASURED_YET.put("BookingNotificationQueryService", "IT8 以前。通知の一覧");
        NOT_MEASURED_YET.put("CorrectionQueryService", "IT12 以前。訂正・取り消しの承認待ち");
        NOT_MEASURED_YET.put("CustomsQueryService", "IT11 以前。通関申告の一覧");
        NOT_MEASURED_YET.put("LockedAccountQueryService", "IT5 以前。ロック済みアカウント");
        NOT_MEASURED_YET.put("RouteProposalQueryService", "IT4 以前。経路候補");
        NOT_MEASURED_YET.put("TrackingExceptionQueryService", "IT10 以前。未解決の例外");
    }

    /**
     * <strong>一覧を返すクエリサービスは、問い合わせ回数を計測している。</strong>
     *
     * <p>違反があればサービス名を並べて落とす。
     */
    @Test
    void 一覧を返すクエリサービスには計測がある() throws IOException {
        Set<String> listServices = listReturningQueryServices();
        assertThat(listServices)
                .as("一覧を返すクエリサービスが 1 つも見つからないなら、検査は何も見ていない")
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
                        （1 件のときと 5 件のときで回数が変わらないこと）。""")
                .isEmpty();
    }

    /**
     * <strong>据え置きの表に、もう解消したものを残さない。</strong>
     *
     * <p>返したのに名前が残っていると、<strong>表が縮んでいないように見える</strong>。
     * 縮まない表は、いずれ読まれなくなる。
     */
    @Test
    void 据え置きの表に解消済みのものを残さない() throws IOException {
        Set<String> stale = new TreeSet<>(NOT_MEASURED_YET.keySet());
        stale.retainAll(measuredServices());

        assertThat(stale)
                .as("計測を書いたクエリサービスが据え置きの表に残っています。表から消してください")
                .isEmpty();
    }

    /**
     * <strong>据え置きの表に、存在しないサービスを残さない。</strong>
     *
     * <p>名簿方式は<strong>実体を失った行に気づけない</strong>
     * （{@code CrossContextPortPolicyTest} が同じ検査を持つ）。
     */
    @Test
    void 据え置きの表に実在しないサービスを残さない() throws IOException {
        Set<String> phantom = new TreeSet<>(NOT_MEASURED_YET.keySet());
        phantom.removeAll(listReturningQueryServices());

        assertThat(phantom)
                .as("据え置きの表に実在しないクエリサービスがあります")
                .isEmpty();
    }

    /** {@code List<} を返すメソッドを持つ {@code *QueryService} インターフェース。 */
    private static Set<String> listReturningQueryServices() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Path source : javaFilesUnder(MAIN)) {
            String fileName = source.getFileName().toString();
            if (!fileName.endsWith("QueryService.java") || fileName.startsWith("MyBatis")) {
                continue;
            }
            // **インターフェースとは限らない。** 実装を持たないクラスとして
            // 書かれているものもある（BookingNotificationQueryService / RouteProposalQueryService）。
            // **形で絞ると、形が違うものが漏れる。**
            if (Files.readString(source).contains("List<")) {
                names.add(fileName.substring(0, fileName.length() - ".java".length()));
            }
        }
        return names;
    }

    /** {@code QueryCounter} と一緒に名前が現れるクエリサービス。 */
    private static Set<String> measuredServices() throws IOException {
        Set<String> measured = new LinkedHashSet<>();
        Set<String> services = listReturningQueryServices();
        for (Path source : javaFilesUnder(TEST)) {
            // **検査自身を「計測済み」と数えない。** 据え置きの表に名前を持つため、
            // 除外しないと**すべてが計測済みに見える**（自己参照）
            if (source.getFileName().toString().equals("ListQueryMeasurementTest.java")) {
                continue;
            }
            String text = Files.readString(source);
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

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
