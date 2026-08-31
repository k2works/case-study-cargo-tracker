package com.example.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.architecture.HexagonalArchitectureRules.TokenHandling;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アーキテクチャ検査が全サービスに行き渡っていることを検査するメタテスト。
 *
 * <p>名簿方式の検査は「載っていないもの」を素通りさせるため、載せ忘れたサービスほど無検査になる。
 * ここでは 2 つの方向から、名簿を持たずに漏れを検出する。
 *
 * <ol>
 *   <li><strong>規則の側</strong>: {@link HexagonalArchitectureRules} が公開する規則が、
 *       {@link HexagonalArchitectureRules#allServiceRules} に本当に含まれているかを
 *       リフレクションで確かめる。規則を足して束ねる側に入れ忘れると落ちる
 *   <li><strong>サービスの側</strong>: settings.gradle のサブプロジェクトすべてが
 *       {@link ServiceArchitectureTest} を継承しているかを確かめる。継承していれば、
 *       規則の一覧はサービス側に写されないため、写し漏れという失敗自体が起きない
 * </ol>
 *
 * <p>3 つ目として、<strong>マイグレーションを持つサービスに方言スモークがあるか</strong>も
 * 見る。テスト戦略には「入口を持つ全サービスに置く」と書いてあったが、検査が無いあいだに
 * billingms と simulationms の 2 つが抜けていた（IT15 で発見）。規則は、同じ変更で検査に
 * 落とさなければ守られない。
 *
 * <p>IT6 までは「各サービスの ArchitectureTest が規則名を含むか」をソース文字列で見ていた。
 * この形は規則が増えるたびに 7 サービスへ手で写す必要があり、しかも Javadoc に名前が
 * 出ているだけでも通ってしまう。適用そのものを基底クラスへ寄せて、写す作業を無くした。
 */
class ArchitectureRuleCoverageTest {

    /** shared はライブラリであり、サービスのレイヤー構造を持たないため検査対象外とする。 */
    private static final List<String> NOT_A_SERVICE = List.of("shared");

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /** 規則を束ねる側が満たすかを確かめるときに使う、実在しないサービス名。 */
    private static final String PROBE_SERVICE = "probems";

    /**
     * 全サービスへ掛けるのではない規則。理由とともに並べる。
     *
     * <p>「まだ束ねていない」と「束ねないと決めた」は違う。並べたまま放置されないよう、
     * なぜ束ねないのかを書く。<strong>免除は名簿でよい</strong>——載せ忘れれば
     * 「束ねられていない」と落ちる側に倒れるためである。
     */
    private static final Map<String, String> EXEMPT = Map.of(
            "sharedKernelScopeRule", "共有カーネル自体の規則。shared の SharedKernelScopeTest が 1 回検査する",
            "allServiceRules", "束ねる側そのもの");

    @Test
    @DisplayName("公開されている規則はすべて allServiceRules に束ねられている")
    void everyPublishedRuleIsBundled() {
        Set<String> bundled = new LinkedHashSet<>();
        for (TokenHandling handling : TokenHandling.values()) {
            HexagonalArchitectureRules.allServiceRules(PROBE_SERVICE, handling)
                    .forEach(rule -> bundled.add(rule.getDescription()));
        }
        assertThat(bundled)
                .as("束ねられた規則が 0 件なら、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (Method method : ruleMethods()) {
            for (String description : descriptionsOf(method)) {
                if (!bundled.contains(description)) {
                    missing.add("%s（%s）".formatted(method.getName(), description));
                }
            }
        }

        assertThat(missing)
                .as("公開されているが allServiceRules に束ねられていない規則。"
                        + "束ねないと決めたなら EXEMPT に理由を書くこと")
                .isEmpty();
    }

    @Test
    @DisplayName("settings.gradle に載る全サービスが共通の基底を継承している")
    void everyServiceExtendsTheSharedBase() throws IOException {
        List<String> services = services();
        assertThat(services)
                .as("サービスが 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (String service : services) {
            Path test = architectureTestOf(service);
            if (!Files.exists(test)) {
                problems.add("%s に ArchitectureTest が無い".formatted(service));
                continue;
            }
            String source = Files.readString(test);
            if (!EXTENDS_BASE.matcher(source).find()) {
                problems.add("%s の ArchitectureTest が %s を継承していない"
                        .formatted(service, ServiceArchitectureTest.class.getSimpleName()));
            }
        }

        assertThat(problems)
                .as("アーキテクチャ検査が未適用のサービス")
                .isEmpty();
    }

    @Test
    @DisplayName("マイグレーションを持つ全サービスに方言スモークがある")
    void everyServiceWithMigrationsHasADialectSmoke() throws IOException {
        List<String> withMigrations = services().stream()
                .filter(service -> Files.isDirectory(migrationsOf(service)))
                .toList();

        assertThat(withMigrations)
                .as("マイグレーションを持つサービスが 1 つも読み取れていない場合、"
                        + "この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = withMigrations.stream()
                .filter(service -> !Files.exists(dialectSmokeOf(service)))
                .toList();

        assertThat(missing)
                .as("方言スモークが無いサービス。DB を持つなら、その SQL とマイグレーションが"
                        + "PostgreSQL（本番）と H2（ローカルの手軽な起動先）の両方で"
                        + "解釈できることを確かめる")
                .isEmpty();
    }

    private Path migrationsOf(String service) {
        return BACKEND_ROOT.resolve(service).resolve("src/main/resources/db/migration");
    }

    private Path dialectSmokeOf(String service) {
        return BACKEND_ROOT.resolve(service)
                .resolve("src/test/java/com/example/%s/DialectSmokeTest.java".formatted(service));
    }

    /**
     * 継承の宣言。Javadoc 中の言及と区別するため、{@code extends} を伴う形だけを認める。
     *
     * <p>クラス名を書き写さず、基底クラス自身から組み立てる。書き写すと、改名したときに
     * 検査だけが取り残される。
     */
    private static final Pattern EXTENDS_BASE = Pattern.compile(
            "\\bextends\\s+(?:[\\w.]+\\.)?" + ServiceArchitectureTest.class.getSimpleName() + "\\b");

    /** 公開されている規則メソッド。手で並べず、命名（{@code Rule} / {@code Rules} で終わる）から導く。 */
    private static List<Method> ruleMethods() {
        return java.util.Arrays.stream(HexagonalArchitectureRules.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getName().endsWith("Rule") || method.getName().endsWith("Rules"))
                .filter(method -> !EXEMPT.containsKey(method.getName()))
                .sorted(java.util.Comparator.comparing(Method::getName))
                .toList();
    }

    /** 規則メソッドを実際に呼び、その説明を得る。説明で照合するのは、束ねる側が同じ規則を作ったことの証拠になるため。 */
    private static List<String> descriptionsOf(Method method) {
        Object[] arguments = switch (method.getParameterCount()) {
            case 0 -> new Object[0];
            case 1 -> new Object[] {argumentFor(method)};
            default -> throw new IllegalStateException(
                    "引数が 2 つ以上の規則メソッドは想定していない: " + method.getName());
        };
        Object result;
        try {
            result = method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("規則メソッドを呼べない: " + method.getName(), e);
        }
        if (result instanceof ArchRule rule) {
            return List.of(rule.getDescription());
        }
        if (result instanceof List<?> rules) {
            return rules.stream().map(ArchRule.class::cast).map(ArchRule::getDescription).toList();
        }
        throw new IllegalStateException("規則でも規則の一覧でもない戻り値: " + method.getName());
    }

    /** 規則メソッドはサービス名かベースパッケージのどちらかを取る。{@code layerRules} だけが後者。 */
    private static String argumentFor(Method method) {
        return "layerRules".equals(method.getName())
                ? "com.example." + PROBE_SERVICE
                : PROBE_SERVICE;
    }

    private Path architectureTestOf(String service) {
        return BACKEND_ROOT.resolve(service)
                .resolve("src/test/java/com/example/%s/ArchitectureTest.java".formatted(service));
    }

    private List<String> services() throws IOException {
        String settings = Files.readString(BACKEND_ROOT.resolve("settings.gradle"));
        Matcher matcher = Pattern.compile("^include\\s+'([^']+)'", Pattern.MULTILINE).matcher(settings);
        List<String> services = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!NOT_A_SERVICE.contains(name)) {
                services.add(name);
            }
        }
        return services;
    }
}
