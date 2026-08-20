package com.example.shared.architecture;

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
 * 利用者ヘッダの検査が全サービスに行き渡っていることを検査するメタテスト（ADR-007 の決定 3）。
 *
 * <p>この検査は「登録したサービスの一覧」を持たない。持つと、載せ忘れたサービスほど無検査になる。
 * 代わりに settings.gradle のサブプロジェクトを正とし、<strong>除外に載っていないサービスは
 * 登録必須</strong>とする。未登録は素通りではなく赤になる。
 */
class AuthenticatedUserFilterRegistrationTest {

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 除外。
     *
     * <p>shared はライブラリでありサービスではない。gatewayms は署名検証を行いヘッダを
     * 付ける側であり、ヘッダを要求する側ではない（要求すると誰もログインできない）。
     */
    private static final List<String> EXEMPT = List.of("shared", "gatewayms");

    private static final Pattern REGISTERS_FILTER =
            Pattern.compile("new\\s+AuthenticatedUserFilter\\s*\\(");

    @Test
    @DisplayName("除外に載っていない全サービスが利用者ヘッダの検査を登録している")
    void everyServiceRegistersTheFilter() throws IOException {
        List<String> services = services();
        assertThat(services)
                .as("サービスが 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String service : services) {
            if (EXEMPT.contains(service)) {
                continue;
            }
            if (!registersFilter(service)) {
                missing.add(service);
            }
        }

        assertThat(missing)
                .as("AuthenticatedUserFilter を登録していないサービス（ADR-007）。"
                        + "登録しないと、認可を書き忘れたエンドポイントが無認証で開く")
                .isEmpty();
    }

    private boolean registersFilter(String service) throws IOException {
        Path main = BACKEND_ROOT.resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(main)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (REGISTERS_FILTER.matcher(Files.readString(file)).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> services() throws IOException {
        String settings = Files.readString(BACKEND_ROOT.resolve("settings.gradle"));
        Matcher matcher = Pattern.compile("^include\\s+'([^']+)'", Pattern.MULTILINE).matcher(settings);
        List<String> services = new ArrayList<>();
        while (matcher.find()) {
            services.add(matcher.group(1));
        }
        return services;
    }
}
