package com.example.cargotracker.shared.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 規則が<b>全サービスに当たっていること</b>を検査する（IT1 タスク 2.1）。
 *
 * <p>規則を書いても、当て忘れたサービスは名乗り出ない。当て忘れは「そのサービスだけ
 * 検査されていない」状態で、いちばん気づきにくい。サービスを増やしたときに、この検査が
 * 落ちて気づけるようにする。</p>
 */
class ArchRulesAreAppliedTest {

    private static Path backendRoot() {
        return Path.of("").toAbsolutePath().getParent();
    }

    /** 業務サービス（shared とテスト専用を除く）。BuildConventionTest の名簿と対にする。 */
    static Stream<String> services() {
        return Stream.of("gatewayms", "authms", "bookingms", "routingms",
                "trackingms", "handlingms", "billingms");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("services")
    @DisplayName("全サービスが境界の規則を適用している")
    void everyServiceAppliesTheRules(String service) throws IOException {
        Path serviceTestRoot = backendRoot().resolve(service).resolve("src/test/java");

        assertThat(serviceTestRoot)
                .as("%s: テストソースが無い。規則を当てる場所が無い", service)
                .exists();

        try (Stream<Path> paths = Files.walk(serviceTestRoot)) {
            List<Path> applying = paths
                    .filter(p -> p.toString().endsWith("ArchitectureTest.java"))
                    .filter(p -> containsBaseClass(p))
                    .toList();

            assertThat(applying)
                    .as("%s: AbstractServiceArchitectureTest を継承したテストが無い。"
                            + "サービスを増やしたら規則の適用も足す", service)
                    .hasSize(1);
        }
    }

    private static boolean containsBaseClass(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains("extends AbstractServiceArchitectureTest");
        } catch (IOException e) {
            return false;
        }
    }
}
