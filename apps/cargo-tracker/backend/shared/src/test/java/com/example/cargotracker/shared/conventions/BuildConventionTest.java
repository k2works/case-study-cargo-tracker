// パッケージ名を build にしない。.gitignore の `build/` に一致して、
// ここのファイルが丸ごと git に入らないまま「検査がある」と思い込むことになる
// （実際に BuildConventionTest が一度も追跡されていなかった）。
package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0001 のコンプライアンス欄を検査として置いたもの。
 *
 * <p>決定を文章のまま置くと守られないので、決定の数だけ検査を用意する。ここで見るのは
 * ビルド構成そのもの（サービスの数・Axon の版の揃い）で、コードの構造は ArchUnit が見る。</p>
 */
class BuildConventionTest {

    /** 業務サブプロジェクト。テスト専用（contract-tests・acceptance-tests）は数えない。 */
    private static final List<String> BUSINESS_PROJECTS = List.of(
            "shared", "gatewayms", "authms", "bookingms",
            "routingms", "trackingms", "handlingms", "billingms");

    private static final List<String> TEST_ONLY_PROJECTS = List.of("contract-tests", "acceptance-tests");

    private static Path backendRoot() {
        // テストの作業ディレクトリはサブプロジェクト（shared）なので 1 つ上がバックエンドのルート。
        return Path.of("").toAbsolutePath().getParent();
    }

    private static String read(String relative) throws IOException {
        return Files.readString(backendRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static List<String> includedProjects() throws IOException {
        Matcher m = Pattern.compile("^include\\(\"([^\"]+)\"\\)", Pattern.MULTILINE)
                .matcher(read("settings.gradle.kts"));
        return m.results().map(r -> r.group(1)).collect(Collectors.toList());
    }

    @Test
    @DisplayName("ADR-0001 決定 1: 業務サブプロジェクトはテスト専用を除いて 8 つで、名簿と一致する")
    void businessProjectsMatchTheRoster() throws IOException {
        List<String> business = includedProjects().stream()
                .filter(p -> !TEST_ONLY_PROJECTS.contains(p))
                .toList();

        assertThat(business)
                .as("サービスを増やす・減らすときは ADR-0001 を改訂してからこの名簿を直す")
                .containsExactlyElementsOf(BUSINESS_PROJECTS);
    }

    @Test
    @DisplayName("ADR-0001 決定 1: テスト専用サブプロジェクトは include されているが業務サービスには数えない")
    void testOnlyProjectsAreIncludedButNotCounted() throws IOException {
        assertThat(includedProjects()).containsAll(TEST_ONLY_PROJECTS);
    }

    @Test
    @DisplayName("ADR-0001 決定 3: Axon の成果物はすべて単一の version.ref を参照する")
    void allAxonArtifactsShareOneVersionRef() throws IOException {
        String catalog = read("gradle/libs.versions.toml");

        // module に axon を含む libraries 行を「版の書き方によらず」すべて拾う。
        // version.ref を持つ行だけを拾うと、版を直書きした行が検査を素通りする。
        Matcher m = Pattern.compile(
                        "^[\\w-]+\\s*=\\s*\\{[^}]*module\\s*=\\s*\"[^\"]*axonframework[^\"]*\"[^}]*\\}",
                        Pattern.MULTILINE)
                .matcher(catalog);

        var axonLines = m.results().map(r -> r.group(0)).toList();
        assertThat(axonLines)
                .as("Axon の 3 成果物がカタログに載っていること")
                .hasSizeGreaterThanOrEqualTo(3);

        for (String line : axonLines) {
            assertThat(line)
                    .as("Axon の依存は版を直書きせず version.ref = \"axon\" を使う。"
                            + "starter / connector / axon-test の版がずれると Axon Server に接続できない"
                            + "（IT1 スパイクで実測。ADR-0001 決定 3）")
                    .containsPattern("version\\.ref\\s*=\\s*\"axon\"");
        }

        assertThat(catalog)
                .contains("axon-spring-boot-starter")
                .contains("axon-server-connector")
                .contains("axon-test");
    }

    @Test
    @DisplayName("ADR-0001 決定 3: axon-server-connector を明示依存として全業務サービスが持つ")
    void everyServiceDeclaresTheServerConnector() throws IOException {
        for (String service : BUSINESS_PROJECTS) {
            if (service.equals("shared")) {
                continue; // 共有カーネルは接続を持たない
            }
            assertThat(read(service + "/build.gradle.kts"))
                    .as("%s: connector は starter の推移的依存に含まれない。無いと無音で in-memory に落ちる", service)
                    .contains("libs.bundles.axon");
        }
    }
}
