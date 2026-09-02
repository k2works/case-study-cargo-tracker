package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 各サービスが<strong>自分のデータベース</strong>を向いていることを検査する。
 *
 * <p><strong>実環境でしか出ない形の欠陥である</strong>（IT14 で実際に起きた）。
 * simulationms のマニフェストが他サービスの DB（{@code billing_db}）を指していた。
 * 写して作ったマニフェストで接続先だけ直し忘れると、<strong>ユニット・結合・モックの
 * すべてが緑のまま</strong>、実環境で起動しない。
 *
 * <p>止めたのは Flyway の検証だった（他サービスの履歴と食い違って落ちた）。
 * <strong>止まらない組み合わせもある</strong>——履歴が無い相手なら、他サービスの DB に
 * テーブルを作ってしまう。偶然に頼らず、ここで落とす。
 *
 * <p>名簿は持たない。マニフェストの実体から集める——名簿方式にすると、
 * 載せ忘れたサービスほど無検査で残る。
 */
@DisplayName("サービスとデータベースの対応")
class ServiceDatabaseUrlTest {

    private static final Path MANIFESTS = Path.of("..").toAbsolutePath()
            .resolve("../../ops/k8s/kustomize/base").normalize();

    /** {@code jdbc:postgresql://postgres:5432/xxx_db} の検出。 */
    private static final Pattern JDBC_URL =
            Pattern.compile("jdbc:postgresql://[^/\\s]+/([a-z_]+)");

    @Test
    @DisplayName("どのサービスも、自分の名前のデータベースを向いている")
    void everyServicePointsAtItsOwnDatabase() throws IOException {
        List<Path> manifests = manifests();

        assertThat(manifests)
                .as("接続先を書いたマニフェストが 1 つも無い場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path manifest : manifests) {
            String service = manifest.getFileName().toString().replace(".yaml", "");
            String expected = service.replaceAll("ms$", "") + "_db";
            Matcher matcher = JDBC_URL.matcher(Files.readString(manifest));
            while (matcher.find()) {
                if (!expected.equals(matcher.group(1))) {
                    violations.add("%s が %s ではなく %s を向いている"
                            .formatted(service, expected, matcher.group(1)));
                }
            }
        }

        assertThat(violations)
                .as("他サービスのデータベースを向いている。写して作ったマニフェストで"
                        + "接続先だけ直し忘れると、実環境で初めて分かる")
                .isEmpty();
    }

    /**
     * シミュレーションが名乗る利用者。
     *
     * <p>環境変数の値を読む。{@code APP_SIMULATION_USER_*}（どの利用者として工程を踏むか）と
     * {@code APP_SIMULATION_REGISTRAR_USERNAMES}（由来つきで荷主を登録してよい利用者）の 2 つ。
     */
    private static final Pattern SIMULATION_USER_ENV = Pattern.compile(
            "name:\\s*(APP_SIMULATION_USER_[A-Z]+|APP_SIMULATION_REGISTRAR_USERNAMES)\\s*\\n"
                    + "\\s*value:\\s*\"?([^\"\\n]+)\"?");

    /**
     * <strong>実業務の利用者を借りていないこと</strong>（[ADR-030] 決定 2・IT15）。
     *
     * <p>IT14 は sales01 として動かしていた。「シミュレーション由来として登録してよい」
     * 名簿にも sales01 が載るため、<strong>実の営業担当者が自分の登録を由来つきにできた</strong>
     * ——精算の締めから消える操作である。
     *
     * <p>接続先の取り違えと同じ族なので、検査を分けずここに足す（IT14 Try 4）。
     * どちらも「マニフェストの値が別の誰かを指している」形であり、見る場所は 1 つでよい。
     */
    @Test
    @DisplayName("シミュレーションは専用の利用者としてしか名乗らない")
    void simulationNamesOnlyDedicatedUsers() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(MANIFESTS)) {
            for (Path manifest : files.filter(path -> path.toString().endsWith(".yaml")).toList()) {
                Matcher matcher = SIMULATION_USER_ENV.matcher(Files.readString(manifest));
                while (matcher.find()) {
                    for (String username : matcher.group(2).split(",")) {
                        found.add(username.trim());
                        if (!username.trim().startsWith(SIMULATION_USERNAME_PREFIX)) {
                            violations.add("%s の %s が %s を指している"
                                    .formatted(manifest.getFileName(), matcher.group(1),
                                            username.trim()));
                        }
                    }
                }
            }
        }

        assertThat(found)
                .as("シミュレーションの利用者を書いたマニフェストが 1 つも無い場合、"
                        + "この検査は何も守らない")
                .isNotEmpty();
        assertThat(violations)
                .as("実業務の利用者を借りている。借りると、その利用者本人も"
                        + "「シミュレーション由来」として登録でき、精算の締めから消せる")
                .isEmpty();
    }

    /**
     * シミュレーション専用の利用者名の帯。
     *
     * <p>simulationms の定数を書き写している。shared から実サービスのクラスは見えない——
     * <strong>写した以上、食い違えばこの検査が赤くなる側に倒れる</strong>
     * （帯を広げる変更なら simulationms 側が先に赤くなる）。
     */
    private static final String SIMULATION_USERNAME_PREFIX = "sim-";

    private static List<Path> manifests() throws IOException {
        try (Stream<Path> files = Files.list(MANIFESTS)) {
            return files.filter(path -> path.toString().endsWith(".yaml"))
                    .filter(ServiceDatabaseUrlTest::declaresJdbcUrl)
                    .sorted()
                    .toList();
        }
    }

    private static boolean declaresJdbcUrl(Path path) {
        try {
            return JDBC_URL.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
