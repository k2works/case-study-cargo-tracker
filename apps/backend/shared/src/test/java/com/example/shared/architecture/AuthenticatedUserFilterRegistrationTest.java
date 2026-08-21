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

    /** REST の入口を持つサービスかどうかを、実在のコントローラから判定する。 */
    private static final Pattern HAS_REST_ENDPOINT = Pattern.compile("@RestController");

    /** フィルタが<strong>働く</strong>ことを確かめている検査。 */
    private static final Pattern VERIFIES_REJECTION = Pattern.compile("isUnauthorized\\s*\\(");

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

    /**
     * 入口を持つサービスは、フィルタが<strong>働くこと</strong>まで確かめる。
     *
     * <p>登録の字面があっても、除外パスを広げれば業務エンドポイントは開く。
     * IT3 までの検査はソースの正規表現一致にとどまり、**公開パスを広げても緑**だった。
     * 安全装置は「入れたこと」ではなく「働くこと」で確かめる（IT4 タスク 0.3）。
     *
     * <p>対象は名簿ではなく、{@code @RestController} を持つサービスとして<strong>コードから導く</strong>。
     * 入口を足したサービスは、それだけでこの検査の対象になる。
     */
    @Test
    @DisplayName("REST の入口を持つサービスは、ヘッダ無しが拒否されることを検査している")
    void everyServiceWithEndpointsVerifiesRejection() throws IOException {
        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();

        for (String service : services()) {
            if (EXEMPT.contains(service) || !hasRestEndpoint(service)) {
                continue;
            }
            checked.add(service);
            if (!verifiesRejection(service)) {
                missing.add(service);
            }
        }

        assertThat(checked)
                .as("入口を持つサービスが 1 つも見つからない場合、この検査は何も守らない")
                .isNotEmpty();
        assertThat(missing)
                .as("ヘッダ無しのリクエストが 401 になることを確かめていないサービス。"
                        + "登録しただけでは、除外パスを広げたときに気づけない")
                .isEmpty();
    }

    private boolean hasRestEndpoint(String service) throws IOException {
        return matchesInJavaFiles(BACKEND_ROOT.resolve(service).resolve("src/main/java"),
                HAS_REST_ENDPOINT);
    }

    private boolean verifiesRejection(String service) throws IOException {
        return matchesInJavaFiles(BACKEND_ROOT.resolve(service).resolve("src/test/java"),
                VERIFIES_REJECTION);
    }

    private boolean matchesInJavaFiles(Path root, Pattern pattern) throws IOException {
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (pattern.matcher(Files.readString(file)).find()) {
                    return true;
                }
            }
        }
        return false;
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
