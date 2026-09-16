package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Event Sourcing のサービスは同じ形で立ち上げる。
 *
 * <p>bookingms で得た知見（IT2）を routingms で入れ直したのは、形が同じであることを
 * どこにも書いていなかったからです。<b>3 つ目のサービス（trackingms / IT8）で同じ失敗を
 * 繰り返さないために、形そのものを検査に落とします。</b></p>
 *
 * <p>{@code @EventTag} は {@link EventTagAccompaniesEventSourcedTest} が見ています。
 * ここで見るのは残りの 3 つです。</p>
 *
 * <ol>
 *   <li>コマンドハンドラが static でない（static が勝つと 2 度目の受付が素通りする）</li>
 *   <li>投影のパッケージが {@code application.yml} の Processing Group に列挙されている
 *       （列挙し忘れると既定の設定で動くので、テストは緑のまま本番だけ挙動が変わる）</li>
 *   <li>投影を持つサービスに {@code ReplayIT} がある（リプレイで副作用が積み上がらない
 *       ことは、静的な依存では確かめられない）</li>
 * </ol>
 *
 * <p><b>正しい形のものだけを探しません。</b> {@code @EventSourced} が付いた集約を全部
 * 拾ってから、それぞれについて 3 つを見ます。載っているものだけを数える検査は、
 * 載せ忘れたものほど漏らします。</p>
 */
class EventSourcedServicesHaveTheSameShapeTest {

    // 行頭のものだけを拾う。Javadoc で {@code @EventSourced(tagKey)} に触れている
    // イベントやユーティリティを集約と取り違えない。
    private static final Pattern EVENT_SOURCED =
            Pattern.compile("(?m)^@EventSourced\\s*\\(");
    /**
     * 修飾子は順不同でまとめて拾ってから {@code static} の有無を見る。
     * {@code (public)?\\s*(static)?} の形にすると {@code static public} の順で
     * 書かれたときに素通りする。この検査が守っているのは「2 度目の受付が通る」
     * という IT2 の実測欠陥そのものなので、書き方の違いで空振りしてはいけない。
     */
    private static final Pattern COMMAND_HANDLER_SIGNATURE = Pattern.compile(
            "@CommandHandler\\s*(?://[^\\n]*\\n\\s*)*"
                    + "((?:(?:public|protected|private|static|final|synchronized)\\s+)*)");

    private static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    /** Event Sourcing の集約と、それが属するサービスのディレクトリ。 */
    private record Aggregate(Path file, Path serviceDir, String servicePackage) {
    }

    private static List<Aggregate> eventSourcedAggregates() throws IOException {
        List<Aggregate> found = new ArrayList<>();
        for (Path file : mainSources()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            if (!EVENT_SOURCED.matcher(body).find()) {
                continue;
            }
            String path = file.toString().replace('\\', '/');
            int at = path.indexOf("/src/main/java/");
            Path serviceDir = Path.of(path.substring(0, at));
            Matcher pkg = Pattern.compile("package\\s+([\\w.]+)\\.domain\\.model")
                    .matcher(body);
            found.add(new Aggregate(file, serviceDir, pkg.find() ? pkg.group(1) : null));
        }
        return found;
    }

    @Test
    @DisplayName("Event Sourcing の集約が 2 つ以上ある（検査が空振りしていない）")
    void thereAreAggregatesToCheck() throws IOException {
        // 対象が 0 件でも他の検査は緑になる。まず対象があることを見る。
        assertThat(eventSourcedAggregates()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("コマンドハンドラは static でない")
    void commandHandlersAreInstanceMethods() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            String body = Files.readString(aggregate.file(), StandardCharsets.UTF_8);
            Matcher matcher = COMMAND_HANDLER_SIGNATURE.matcher(body);
            while (matcher.find()) {
                if (matcher.group(1).contains("static")) {
                    offenders.add(aggregate.file().getFileName().toString());
                }
            }
        }
        assertThat(offenders)
                .as("static の作成ハンドラを置くと、集約が既に存在しても static が呼ばれ、"
                        + "2 度目の受付が通る（IT2 で実測）")
                .isEmpty();
    }

    @Test
    @DisplayName("Event Sourcing のサービスは共有カーネルの対応表を使う（写さない）")
    void servicesReuseTheSharedExceptionMapping() throws IOException {
        // 「連鎖の途中の包みまで見る」（containsMarker / deepestMessage）は IT3 で
        // 受け入れテストが初めて出した知見で、**4 サービスに写していた**（IT9 H.2）。
        // 写しは文言と説明が少しずつ食い違い、片方だけ直る。IT10 で共有カーネルへ
        // 抽出したので、いま見るのは「写していないこと」である。
        List<String> offenders = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            Path handler = aggregate.serviceDir().resolve("src/main/java")
                    .resolve(aggregate.servicePackage().replace('.', '/'))
                    .resolve("interfaces/rest/ApiExceptionHandler.java");
            if (!Files.exists(handler)) {
                offenders.add(aggregate.serviceDir().getFileName() + ": ApiExceptionHandler が無い");
                continue;
            }
            String body = Files.readString(handler, StandardCharsets.UTF_8);
            if (!body.contains(
                    "extends com.example.cargotracker.shared.interfaces.rest.AbstractApiExceptionHandler")) {
                offenders.add(aggregate.serviceDir().getFileName() + ": 共有の対応表を継承していない");
            }
            // 共通の処理を写し戻したら赤にする。
            for (String copied : List.of("containsMarker", "deepestMessage",
                    "@ExceptionHandler(BusinessRuleViolation.class)")) {
                if (body.contains(copied)) {
                    offenders.add(aggregate.serviceDir().getFileName()
                            + ": 共有の対応表にあるものを写している（" + copied + "）");
                }
            }
        }

        assertThat(offenders)
                .as("対応表が複数あると、包みの見方が片方だけ直る（IT3 で実測・IT9 H.2）")
                .isEmpty();
    }

    @Test
    @DisplayName("対応表を持たないサービスは、ドメイン例外も投げていない（IT10 H.5）")
    void servicesWithoutTheSharedMappingDoNotThrowDomainErrors() throws IOException {
        // **「対応表は 1 つ」と宣言した IT で、持たないサービスが 1 つ残った。**
        // authms は Event Sourcing ではないので上の検査の外にいる（ADR-0001）。
        // 外にいること自体は正しいが、**外にいるサービスがドメイン例外を投げ始めたら
        // 500 に化ける**——検査は配り先の数だけ確かめる。
        List<String> offenders = new ArrayList<>();
        for (Path serviceDir : servicesWithoutSharedMapping()) {
            Path main = serviceDir.resolve("src/main/java");
            if (!Files.exists(main)) {
                continue;
            }
            try (Stream<Path> sources = Files.walk(main)) {
                for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(source, StandardCharsets.UTF_8);
                    if (body.contains("BusinessRuleViolation")
                            || body.contains("IllegalTransition")) {
                        offenders.add(serviceDir.getFileName() + ": "
                                + main.relativize(source)
                                + " がドメイン例外を投げるが、対応表が無い（500 に化ける）");
                    }
                }
            }
        }

        assertThat(offenders)
                .as("対応表を持たないサービスがドメイン例外を投げると、"
                        + "利用者には 500 としか出ない（IT10 レビュー N5）")
                .isEmpty();
    }

    /**
     * 業務サービスのディレクトリ（{@code *ms}）。
     *
     * <p><b>名簿にしない。</b> 走査で導く——載せ忘れたものほど漏れる。</p>
     */
    private static List<Path> serviceDirs() throws IOException {
        try (Stream<Path> dirs = Files.list(backendRoot())) {
            return dirs.filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().endsWith("ms"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 共有の対応表を継承していないサービス。
     *
     * <p><b>名簿にしない。</b> 走査で導く——載せ忘れたものほど漏れる。</p>
     */
    private static List<Path> servicesWithoutSharedMapping() throws IOException {
        try (Stream<Path> dirs = Files.list(backendRoot())) {
            return dirs.filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().endsWith("ms"))
                    .filter(dir -> {
                        try (Stream<Path> sources = Files.walk(dir.resolve("src/main/java"))) {
                            return sources.filter(p -> p.toString().endsWith(".java"))
                                    .noneMatch(EventSourcedServicesHaveTheSameShapeTest::extendsShared);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted()
                    .toList();
        }
    }

    private static boolean extendsShared(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8).contains(
                    "extends com.example.cargotracker.shared.interfaces.rest"
                            + ".AbstractApiExceptionHandler");
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @DisplayName("共有カーネルの対応表が、包みを 1 枚しか見ない形に戻っていない")
    void sharedMappingStillUnwrapsEveryLayer() throws IOException {
        Path shared = backendRoot().resolve("shared/src/main/java/com/example/cargotracker"
                + "/shared/interfaces/rest/AbstractApiExceptionHandler.java");
        assertThat(shared).as("共有の対応表が無い").exists();

        String body = Files.readString(shared, StandardCharsets.UTF_8);
        assertThat(body)
                .as("包みを 1 枚しか見ないと 409 が 422 に化ける（IT3 で実測）")
                .contains("containsMarker")
                .contains("deepestMessage")
                .contains("@ExceptionHandler(BusinessRuleViolation.class)")
                .contains("@ExceptionHandler(CommandExecutionException.class)");
    }

    @Test
    @DisplayName("@EventHandler を持つパッケージが Processing Group として列挙されている")
    void everyEventHandlerPackageIsEnumerated() throws IOException {
        // **走査するのは「対象になりうるもの」の側である**（IT12 レビュー 中）。
        // 以前は `infrastructure.projection` だけを見ていたので、
        // `application.reaction` のような新しいパッケージを足しても気づけなかった
        // ——bookingms・trackingms は手で列挙して偶然揃っていただけである。
        // 列挙し忘れると既定の設定で動くので、**テストは緑のまま本番だけ**
        // IT11 の壊れ方（1 件で全部止まる）に戻る（[ADR-0014] 決定 2）。
        List<String> missing = new ArrayList<>();
        for (Path serviceDir : serviceDirs()) {
            Path yml = serviceDir.resolve("src/main/resources/application.yml");
            String config = Files.exists(yml)
                    ? Files.readString(yml, StandardCharsets.UTF_8) : "";
            for (String pkg : eventHandlerPackages(serviceDir)) {
                if (!config.contains(pkg)) {
                    missing.add(serviceDir.getFileName() + ": " + pkg);
                }
            }
        }
        assertThat(missing)
                .as("列挙し忘れると既定の設定で動くので、テストは緑のまま本番だけ挙動が変わる")
                .isEmpty();
    }

    /**
     * {@code @EventHandler} を持つクラスのパッケージ。
     *
     * <p>Processing Group はパッケージ名で決まる（{@code @ProcessingGroup} は Axon 5
     * に無い）ので、走査の単位もパッケージである。</p>
     */
    private static List<String> eventHandlerPackages(Path serviceDir) throws IOException {
        Path sources = serviceDir.resolve("src/main/java");
        if (!Files.isDirectory(sources)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(sources)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(EventSourcedServicesHaveTheSameShapeTest::declaresEventHandler)
                    .map(path -> sources.relativize(path.getParent()).toString()
                            .replace('\\', '/').replace('/', '.'))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    private static boolean declaresEventHandler(Path javaFile) {
        try {
            return Files.readString(javaFile, StandardCharsets.UTF_8).contains("@EventHandler");
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("退避先を持つサービスには、処理し直す入口がある")
    void everyServiceWithADeadLetterQueueCanReprocess() throws IOException {
        // **消す手段しか無いと、消すことになる**（[ADR-0014] 決定 1 に反する）。
        // IT12 の実機確認では入口が無く、実際に `DELETE` で片づけた。入口は
        // 共有の Actuator エンドポイントだが、各サービスが @Import しないと
        // Bean にならない（共有設定は明示的に取り込む形にしてある）。
        List<String> withoutEntry = new ArrayList<>();
        for (Path yml : applicationConfigs()) {
            if (!Files.readString(yml, StandardCharsets.UTF_8).contains("dlq:")) {
                continue;
            }
            Path serviceDir = yml.getParent().getParent().getParent().getParent();
            boolean imported;
            try (Stream<Path> paths = Files.walk(serviceDir.resolve("src/main/java"))) {
                imported = paths.filter(path -> path.toString().endsWith("Application.java"))
                        .anyMatch(path -> readFile(path).contains("DeadLetterRetryEndpoint"));
            }
            boolean exposed = Files.readString(yml, StandardCharsets.UTF_8)
                    .contains("deadletters");
            if (!imported || !exposed) {
                withoutEntry.add(serviceDir.getFileName()
                        + (imported ? "" : "（@Import が無い）")
                        + (exposed ? "" : "（actuator に出していない）"));
            }
        }

        assertThat(withoutEntry)
                .as("処理し直せないと、退避を消して片づけることになる（ADR-0014 決定 1）")
                .isEmpty();
    }

    @Test
    @DisplayName("イベントを受けるクラスは処理の列の切り方を宣言している")
    void everyEventHandlingClassDeclaresItsSequence() throws IOException {
        // **既定では列が全体で 1 本になる。** 1 件の毒で無関係の貨物のイベントまで
        // 退避される（IT12 のクラスタ E2E で 4 件のうち 3 件が巻き添え）。
        // `@SequencingPolicy` を書き忘れたクラスだけが、黙って 1 本の列に戻る——
        // 単体の検査はどれも緑のままで、症状はクラスタでしか出ない。
        //
        // **走査するのは「対象になりうるもの」の側**（`@EventHandler` を持つ
        // クラス全部）にする。宣言しているものだけを数えると、書き忘れたものほど漏れる。
        List<String> undeclared = new ArrayList<>();
        for (Path serviceDir : serviceDirs()) {
            Path sources = serviceDir.resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sources)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        // package-info は説明であって、イベントを受けるクラスではない。
                        .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                        .filter(EventSourcedServicesHaveTheSameShapeTest::declaresEventHandler)
                        .filter(path -> !readFile(path).contains("@SequencingPolicy"))
                        .forEach(path -> undeclared.add(serviceDir.getFileName()
                                + ": " + path.getFileName()));
            }
        }

        assertThat(undeclared)
                .as("@SequencingPolicy を書かないと、そのクラスだけ 1 本の列に戻る")
                .isEmpty();
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("列挙した Processing Group には退避先が付いている")
    void everyEnumeratedProcessorHasADeadLetterQueue() throws IOException {
        // **書き忘れを人の注意で防がない**（[ADR-0014] 決定 2）。`"[..default]"` は
        // この版では効かないので、Processor ごとに `dlq.enabled: true` が要る。
        // 書き忘れると、その Processor だけ 1 件で全部止まる形に戻る——
        // テストは緑のまま、本番だけ IT11 の壊れ方をする。
        List<String> withoutDlq = new ArrayList<>();
        for (Path yml : applicationConfigs()) {
            String config = Files.readString(yml, StandardCharsets.UTF_8);
            // 列挙されている Processing Group を全部拾ってから、それぞれについて
            // 見る。dlq が付いているものだけを数えると、付け忘れたものほど漏れる。
            Matcher processors = Pattern
                    .compile("(?m)^ {6}\"\\[(com\\.example\\.cargotracker\\.[^\\]]+)\\]\":"
                            + "((?:\n {8}[^\n]*)*)")
                    .matcher(config);
            while (processors.find()) {
                if (!processors.group(2).contains("dlq:")) {
                    withoutDlq.add(yml.getParent().getParent().getParent().getParent()
                            .getFileName() + ": " + processors.group(1));
                }
            }
        }
        assertThat(withoutDlq)
                .as("退避先の無い Processor は、書けない 1 件で止まり後続が全部届かなくなる")
                .isEmpty();
    }

    /** 業務サービスの {@code application.yml}。Processing Group を列挙している場所。 */
    private static List<Path> applicationConfigs() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> p.toString().replace('\\', '/')
                            .endsWith("/src/main/resources/application.yml"))
                    .toList();
        }
    }

    @Test
    @DisplayName("投影を持つサービスには ReplayIT がある")
    void projectionsHaveAReplayCheck() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            Path projectionDir = aggregate.serviceDir().resolve("src/main/java")
                    .resolve(aggregate.servicePackage().replace('.', '/'))
                    .resolve("infrastructure/projection");
            if (!hasJavaClass(projectionDir)) {
                continue;
            }
            Path testDir = aggregate.serviceDir().resolve("src/test/java");
            if (!containsFileNamed(testDir, "ReplayIT.java")) {
                missing.add(aggregate.serviceDir().getFileName().toString());
            }
        }
        assertThat(missing)
                .as("リプレイで副作用が積み上がらないことは、静的な依存では確かめられない")
                .isEmpty();
    }

    private static boolean hasJavaClass(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.anyMatch(p -> p.getFileName().toString().endsWith(".java")
                    && !p.getFileName().toString().equals("package-info.java"));
        }
    }

    private static boolean containsFileNamed(Path dir, String name) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.anyMatch(p -> p.getFileName().toString().equals(name));
        }
    }
}
