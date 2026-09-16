// パッケージ名を build にしない。.gitignore の `build/` に一致して、
// ここのファイルが丸ごと git に入らないまま「検査がある」と思い込むことになる
// （実際に BuildConventionTest が一度も追跡されていなかった）。
package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0001 決定 6 の再評価の発動条件 1 を検査にしたもの。
 *
 * <p>採用中の Axon に Saga のクラスが現れたら<b>赤にする</b>。壊れたから赤なのではなく、
 * 「前提が変わったので ADR を読み直せ」という合図としての赤である。</p>
 *
 * <p>発動条件を文章だけで持つと、版を上げたときに誰も読み返さない。版を上げた
 * ときにここが落ちて初めて、Reaction Handler を続けるか Saga に移るかを比べられる。</p>
 *
 * <p>2026-09-03 時点の実測: Axon 5.0.0〜5.3.1 のどの成果物にも Saga のクラスは無く、
 * 公式リファレンスも "Sagas do not have a replacement yet in Axon Framework 5." と
 * 書いている。</p>
 */
class SagaIsStillAbsentTest {

    @Test
    @DisplayName("Axon に Saga のクラスが無い（現れたら ADR-0001 決定 6 を再評価する）")
    void axonStillHasNoSaga() {
        List<String> found = sagaClassesOnAxonClasspath();

        assertThat(found)
                .as("Axon に Saga が入った。ADR-0001 決定 6 の再評価の発動条件 1 に当たる。"
                        + "Reaction Handler の自前の状態管理と Saga の関連付け、Deadline の有無、"
                        + "移行の代金を比べて ADR を改訂すること。比べずにこの検査を消さない")
                .isEmpty();
    }

    /** クラスパス上の Axon の jar を開き、saga を含むクラスを集める。 */
    private static List<String> sagaClassesOnAxonClasspath() {
        List<String> found = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path path = Path.of(entry);
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
            if (!fileName.endsWith(".jar") || !fileName.startsWith("axon-")) {
                continue;
            }
            try (JarFile jar = new JarFile(path.toFile())) {
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class") && name.toLowerCase(java.util.Locale.ROOT).contains("saga")) {
                        found.add(fileName + " → " + name);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Axon の jar を開けませんでした: " + path, e);
            }
        }
        return found;
    }

    @Test
    @DisplayName("検査が空振りしていない（Axon の jar を実際に開いている）")
    void actuallyInspectsAxonJars() {
        long axonJars = java.util.Arrays.stream(
                        System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .map(Path::of)
                .map(Path::getFileName)
                .filter(java.util.Objects::nonNull)
                .map(Path::toString)
                .filter(name -> name.startsWith("axon-") && name.endsWith(".jar"))
                .count();

        assertThat(axonJars)
                .as("Axon の jar が 1 つも見つからないなら、上の検査は何も見ていない。"
                        + "「Saga が無い」ではなく「調べていない」で緑になる")
                .isGreaterThanOrEqualTo(5);
    }
}
