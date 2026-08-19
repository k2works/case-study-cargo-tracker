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
     * <p>ファイルの存在だけを見ると、空のクラスや規則を書き忘れたクラスでも緑になる。
     * 名簿方式の弱点が一段ずれた場所で再発するため、呼び出しまで確かめる。
     */
    private static final List<String> REQUIRED_RULES = List.of("layerRules", "serviceIsolationRule");

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

            for (String rule : REQUIRED_RULES) {
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
