package com.example.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アーキテクチャ検査が全サービスに行き渡っていることを検査するメタテスト。
 *
 * <p>名簿方式の検査は「載っていないもの」を素通りさせるため、載せ忘れたサービスほど無検査になる。
 * ここでは settings.gradle のサブプロジェクト一覧を正として、各サービスに ArchitectureTest が
 * 存在することを確認する。新サービスを追加して検査を書き忘れると、このテストが落ちる。
 */
class ArchitectureRuleCoverageTest {

    /** shared はライブラリであり、サービスのレイヤー構造を持たないため検査対象外とする。 */
    private static final List<String> NOT_A_SERVICE = List.of("shared");

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 各サービスが必ず呼ぶべき規則。
     *
     * <p><strong>手で並べない。</strong>{@link HexagonalArchitectureRules} が公開している
     * 規則から導く。手書きの名簿にすると、規則を足しても名簿に写さない限り誰も適用を
     * 強制されない——実際 IT6 で `eventPublishingOnlyInMessagingInfrastructureRule` を
     * 足したとき、bookingms だけが呼び、**AMQP に最も広く触っている trackingms が
     * 無検査のまま**だった。名簿方式の弱点が、それを防ぐためのメタテスト自身で再発していた。
     *
     * <p>適用しない規則は {@link #EXEMPT} に理由つきで並べる。<strong>免除は名簿でよい</strong>
     * ——載せ忘れれば「呼んでいない」と落ちる側に倒れるためである。
     */
    private static List<String> requiredRules() {
        return java.util.Arrays.stream(HexagonalArchitectureRules.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getName().endsWith("Rule")
                        || method.getName().endsWith("Rules"))
                .map(java.lang.reflect.Method::getName)
                .filter(name -> !EXEMPT.containsKey(name))
                .sorted()
                .toList();
    }

    /**
     * サービスごとの適用ではない規則。理由とともに並べる。
     *
     * <p>「まだ書いていない」と「適用しないと決めた」は違う。並べたまま放置されないよう、
     * なぜ適用しないのかを書く。
     */
    private static final java.util.Map<String, String> EXEMPT = java.util.Map.of(
            // JWT 系はサービスごとに適用可否が違う（gatewayms だけが署名検証を担う）。
            // ANY_JWT_RULE / JWT_RULE_EXEMPT で個別に扱う
            "noJwtDependencyRule", "gatewayms だけが署名検証を担うため個別に扱う",
            "noTokenVerificationRule", "同上",
            // 共有カーネルそのものに対する規則であり、サービスごとに呼ぶものではない。
            // shared の SharedKernelScopeTest が 1 回だけ検査する
            "sharedKernelScopeRule", "共有カーネル自体の規則。shared が 1 回検査する");

    /** gatewayms は署名検証を担う唯一のサービスであり、JWT ライブラリ依存の禁止は適用しない。 */
    private static final List<String> ANY_JWT_RULE =
            List.of("noJwtDependencyRule", "noTokenVerificationRule");

    private static final List<String> JWT_RULE_EXEMPT = List.of("gatewayms");

    @Test
    @DisplayName("全サービスが必須の規則を実際に呼んでいる")
    void everyServiceInvokesRequiredRules() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : services()) {
            Path test = architectureTestOf(service);
            if (!Files.exists(test)) {
                continue; // 存在自体は下のテストが落とす
            }
            String source = Files.readString(test);

            for (String rule : requiredRules()) {
                if (!source.contains(rule)) {
                    problems.add("%s が %s を呼んでいない".formatted(service, rule));
                }
            }
            if (!JWT_RULE_EXEMPT.contains(service)
                    && ANY_JWT_RULE.stream().noneMatch(source::contains)) {
                problems.add("%s が ADR-004 の規則を 1 つも呼んでいない".formatted(service));
            }
        }

        assertThat(problems)
                .as("ArchitectureTest はあるが規則を呼んでいないサービス")
                .isEmpty();
    }

    @Test
    @DisplayName("settings.gradle に載る全サービスが ArchitectureTest を持つ")
    void everyServiceHasArchitectureTest() throws IOException {
        List<String> services = services();
        assertThat(services)
                .as("サービスが 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String service : services) {
            if (!Files.exists(architectureTestOf(service))) {
                missing.add(service);
            }
        }

        assertThat(missing)
                .as("アーキテクチャ検査が未適用のサービス。ArchitectureTest を追加すること")
                .isEmpty();
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
