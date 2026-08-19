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

    @Test
    @DisplayName("settings.gradle に載る全サービスが ArchitectureTest を持つ")
    void everyServiceHasArchitectureTest() throws IOException {
        List<String> services = services();
        assertThat(services)
                .as("サービスが 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String service : services) {
            Path test = BACKEND_ROOT.resolve(service)
                    .resolve("src/test/java/com/example/%s/ArchitectureTest.java".formatted(service));
            if (!Files.exists(test)) {
                missing.add(service);
            }
        }

        assertThat(missing)
                .as("アーキテクチャ検査が未適用のサービス。ArchitectureTest を追加すること")
                .isEmpty();
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
