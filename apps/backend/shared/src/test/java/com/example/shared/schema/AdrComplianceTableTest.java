package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR のコンプライアンス表が挙げる検査が、実在することを確かめる。
 *
 * <p><strong>名前で追えない表は、検査ではなく作文である。</strong>IT12 のレビューで、
 * ADR-028 の表が挙げる 5 クラスのうち<strong>すべてがリポジトリに存在しない</strong>
 * ことが分かった（中身は別の名前で守られていたが、表からは辿れなかった）。
 *
 * <p>ADR は「決定ごとに検査を置く」規律の要である。表がずれると、
 * <strong>決定を守っているつもりで守っていない</strong>状態を誰も検出できない。
 */
@DisplayName("ADR のコンプライアンス表")
class AdrComplianceTableTest {

    private static final Path REPOSITORY_ROOT =
            Path.of("../../..").toAbsolutePath().normalize();

    private static final Path ADR = REPOSITORY_ROOT.resolve("docs/adr");

    /** バッククォートで囲まれた「クラス名」または「クラス名#表示名」。 */
    private static final Pattern REFERENCED_TEST = Pattern.compile(
            "`([A-Z][A-Za-z0-9]*Test|[A-Z][A-Za-z0-9]*Rules)(?:[.#][^`]*)?`");

    @Test
    @DisplayName("ADR が挙げる検査クラスは、すべて実在する")
    void everyReferencedTestExists() throws IOException {
        Set<String> declared = referencedTestClasses();

        assertThat(declared)
                .as("ADR から検査クラスを 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        Set<String> existing = existingTestClasses();
        List<String> missing = declared.stream()
                .filter(name -> !existing.contains(name))
                .sorted()
                .toList();

        assertThat(missing)
                .as("ADR が挙げている検査が実在しない。**名前で追えない表は作文である**"
                        + "——決定を守っているつもりで守っていない状態を検出できなくなる")
                .isEmpty();
    }

    private Set<String> referencedTestClasses() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(ADR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".md")).toList()) {
                Matcher matcher = REFERENCED_TEST.matcher(Files.readString(file));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    /** リポジトリにある Java の型名（テストも本体も）。 */
    private Set<String> existingTestClasses() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        List<Path> roots = new ArrayList<>(List.of(REPOSITORY_ROOT.resolve("apps/backend")));
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().contains("/build/"))
                        .forEach(path -> names.add(
                                path.getFileName().toString().replace(".java", "")));
            }
        }
        return names;
    }
}
