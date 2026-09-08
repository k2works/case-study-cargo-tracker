package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 適用済みのマイグレーションは編集しない（IT9 の実測 / IT10 Try T6）。
 *
 * <p><b>CI は緑のまま、動いているクラスタだけが起動しなくなる。</b> Flyway は
 * 適用済みの版の内容ハッシュを持っており、ファイルを書き換えると
 * {@code checksum mismatch} で止まる。CI は毎回まっさらな DB に当てるので、
 * この食い違いはどのテストにも現れない——IT9 では、コメントを 3 行足しただけで
 * trackingms が起動しなくなり、原因に辿り着くまで実クラスタの調査が要った。</p>
 *
 * <p><b>だから内容そのものを台帳で固定する。</b> 版を足すのは自由だが、
 * すでにある版を書き換えたらここが赤になる。<b>直し方は台帳の書き換えではなく、
 * 新しい版を足すこと</b>——台帳を直せば、この検査は何も守らなくなる。</p>
 *
 * <p>台帳の更新が必要になるのは 2 つだけである。新しい版を足したとき（行が増える）と、
 * まだどこにも適用していない版を直すとき（その版だけ値が変わる）。</p>
 */
class AppliedMigrationsAreNotEditedTest {

    /** 台帳。**マイグレーションと同じ変更で更新する**（版を足したときだけ行が増える）。 */
    private static final String LEDGER = "config/migrations/checksums.txt";

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

    /** いまあるマイグレーションの内容ハッシュ（サービス/版 → 値）。 */
    private static Map<String, String> currentChecksums() throws IOException {
        Path root = backendRoot();
        Map<String, String> checksums = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(p -> {
                        String path = p.toString().replace('\\', '/');
                        return path.contains("/src/main/resources/db/migration/")
                                && path.endsWith(".sql");
                    })
                    .sorted()
                    .toList();
            for (Path file : files) {
                checksums.put(root.relativize(file).toString().replace('\\', '/'),
                        sha256(Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return checksums;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えない", e);
        }
    }

    private static Map<String, String> ledger() throws IOException {
        Path path = backendRoot().resolve(LEDGER);
        assertThat(path)
                .as("台帳が無い。マイグレーションを足したら同じ変更でここにも書く")
                .exists();
        Map<String, String> recorded = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = trimmed.split("\\s+", 2);
            recorded.put(cells[1], cells[0]);
        }
        return recorded;
    }

    @Test
    @DisplayName("検査するマイグレーションが実際にある（空振りしていない）")
    void thereAreMigrationsToCheck() throws IOException {
        assertThat(currentChecksums())
                .as("0 件なら、この検査は「編集していない」ではなく「調べていない」")
                .hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("既にある版の内容が変わっていない（変えるなら新しい版を足す）")
    void doesNotEditExistingMigrations() throws IOException {
        Map<String, String> current = currentChecksums();
        Map<String, String> recorded = ledger();

        List<String> edited = recorded.entrySet().stream()
                .filter(entry -> current.containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().equals(current.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(edited)
                .as("適用済みのマイグレーションを書き換えると、CI は緑のまま"
                        + "動いているクラスタだけが checksum mismatch で起動しなくなる。"
                        + "直すのではなく、新しい版を足すこと")
                .isEmpty();
    }

    @Test
    @DisplayName("足した版が台帳に載っている（載せ忘れたものほど守られない）")
    void everyMigrationIsInTheLedger() throws IOException {
        List<String> missing = currentChecksums().keySet().stream()
                .filter(name -> !ledgerHas(name))
                .toList();

        assertThat(missing)
                .as("台帳に無い版は、書き換えても赤にならない")
                .isEmpty();
    }

    @Test
    @DisplayName("消した版が台帳に残っていない（消した版の検査は何も守らない）")
    void ledgerHasNoOrphans() throws IOException {
        Map<String, String> current = currentChecksums();

        List<String> orphans = ledger().keySet().stream()
                .filter(name -> !current.containsKey(name))
                .toList();

        assertThat(orphans)
                .as("消したマイグレーションは台帳からも消す")
                .isEmpty();
    }

    private static boolean ledgerHas(String name) {
        try {
            return ledger().containsKey(name);
        } catch (IOException e) {
            throw new IllegalStateException("台帳を読めない", e);
        }
    }
}
