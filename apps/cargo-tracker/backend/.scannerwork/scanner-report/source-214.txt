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
 * ドメイン層は素の {@code IllegalArgumentException} を投げない（IT2 引き継ぎ 9）。
 *
 * <p>API のエラー対応表は業務規則違反を 422 で返します。ここで
 * {@code IllegalArgumentException} を広く受けていたため、{@code UUID.fromString} の
 * ような<b>プログラミングエラーまで業務規則違反に化けて</b>いました。画面には
 * 「入力が正しくありません」と出るので、利用者は直しようのない入力を直そうとし、
 * こちらは不具合に気づけません。</p>
 *
 * <p>{@code BusinessRuleViolation} は {@code IllegalArgumentException} を継承するので、
 * 寄せても呼び出し側の捕捉は壊れません。変わるのは「業務の判断で断ったこと」が
 * 型で分かるようになる点です。</p>
 *
 * <p><b>正しい形の行だけを探しません。</b> 行頭の {@code throw new } だけを見ていると、
 * {@code if (x) throw new IllegalArgumentException(...)}（同一行）・
 * {@code orElseThrow(() -> new IllegalArgumentException(...))}・
 * {@code Objects.requireNonNull(...)} が全部不可視になります。とくに
 * {@code orElseThrow} は {@code Optional} を使い始めた瞬間に最も出やすい形です。
 * ソース全体から<b>その型を作っている箇所</b>を拾います。</p>
 */
class DomainThrowsBusinessRuleViolationTest {

    /**
     * 例外を作っている箇所。{@code throw new X(} だけでなく
     * {@code orElseThrow(() -> new X(...))} のような形も同じ正規表現で拾う。
     */
    private static final Pattern CONSTRUCTED_EXCEPTION =
            Pattern.compile("new\\s+([A-Z][A-Za-z0-9_]*(?:Exception|Violation|Error|Transition))\\s*\\(");

    /**
     * {@code Objects.requireNonNull} は {@code NullPointerException} を投げる。
     * ドメイン層では業務の判断として表せていないので、これも検出する。
     */
    private static final Pattern REQUIRE_NON_NULL =
            Pattern.compile("\\brequireNonNull\\s*\\(");

    /** ドメイン層で投げてよい例外。ほかは業務の判断として表せていない。 */
    private static final List<String> ALLOWED = List.of(
            "BusinessRuleViolation", "IllegalTransition", "IllegalStateException");

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

    private static List<Path> domainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> {
                        String path = p.toString().replace('\\', '/');
                        return path.contains("/src/main/java/") && path.contains("/domain/");
                    })
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    /** コメントの中の例示は検査しない。 */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
    }

    @Test
    @DisplayName("検査するドメインのソースが実際にある（空振りしていない）")
    void thereAreDomainSourcesToCheck() throws IOException {
        assertThat(domainSources()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("ドメイン層は素の IllegalArgumentException を投げない")
    void domainDoesNotThrowRawIllegalArgument() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : domainSources()) {
            String name = file.getFileName().toString();
            if (name.equals("BusinessRuleViolation.java") || name.equals("IllegalTransition.java")) {
                continue; // 例外そのものの定義。
            }
            String source = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher constructed = CONSTRUCTED_EXCEPTION.matcher(source);
            while (constructed.find()) {
                String type = constructed.group(1);
                if (!ALLOWED.contains(type)) {
                    offenders.add(name + ": " + type);
                }
            }
            if (REQUIRE_NON_NULL.matcher(source).find()) {
                offenders.add(name + ": Objects.requireNonNull");
            }
        }
        assertThat(offenders)
                .as("業務の判断で断ったことが型で分からないと、プログラミングエラーと"
                        + "同じ扱いになって 422 に化ける")
                .isEmpty();
    }
}
