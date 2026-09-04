package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>正しい形の行だけを探しません。</b> ドメイン層の {@code throw new} を全部
 * 拾ってから、型を見ます。</p>
 */
class DomainThrowsBusinessRuleViolationTest {

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
            for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("throw new ")) {
                    continue;
                }
                String type = trimmed.substring("throw new ".length()).split("[(<\\s]")[0];
                if (!ALLOWED.contains(type)) {
                    offenders.add(name + ": " + type);
                }
            }
        }
        assertThat(offenders)
                .as("業務の判断で断ったことが型で分からないと、プログラミングエラーと"
                        + "同じ扱いになって 422 に化ける")
                .isEmpty();
    }
}
