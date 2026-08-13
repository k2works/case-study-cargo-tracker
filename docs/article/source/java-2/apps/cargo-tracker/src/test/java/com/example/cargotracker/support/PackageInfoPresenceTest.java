package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>すべてのパッケージに {@code package-info.java} がある。</strong>
 *
 * <p><strong>パッケージの説明は JIG の出力に載る。</strong> JIG はパッケージ図と
 * ビジネスルール一覧を生成物として出すが、説明が無いパッケージは
 * <strong>パッケージ名だけの箱として描かれる</strong> —— 図を読む人には
 * 「何を入れる場所なのか」が分からない。
 *
 * <p><strong>欠落は人が気づけない。</strong> 新しいパッケージを作るとき、
 * クラスを置けば動く。{@code package-info.java} が無くてもコンパイルは通り、
 * テストも緑になる。<strong>気づくのは JIG の図を眺めたときだけ</strong>であり、
 * それは誰かが眺めたときにしか起きない。
 *
 * <p><strong>実測（IT19）: 74 パッケージ中 14 が欠けていた。</strong>
 * Billing Context はほぼ全域が欠けており、**BC を 1 つ足したときに
 * まとめて漏れる**形だった。
 *
 * <p><strong>説明は「何を入れる場所か」を書く。</strong> クラスの一覧を書き写すと、
 * クラスが増えるたびに古くなる。
 */
@DisplayName("すべてのパッケージに package-info がある")
class PackageInfoPresenceTest {

    @Test
    void クラスを置いたパッケージには説明がある() {
        List<Path> files = SourceScan.main().files();
        assertThat(files)
                .as("走査が空なら、この検査は何も見ていない（IT17 の P1）")
                .isNotEmpty();

        Set<Path> packages = files.stream()
                .map(Path::getParent)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<Path> documented = files.stream()
                .filter(file -> "package-info.java".equals(file.getFileName().toString()))
                .map(Path::getParent)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> missing = new TreeSet<>();
        for (Path directory : packages) {
            if (!documented.contains(directory)) {
                missing.add(directory.toString());
            }
        }

        assertThat(missing)
                .as("""
                        package-info.java が無いパッケージがあります（IT19 の C7）。

                        **説明の無いパッケージは、JIG の図では名前だけの箱になります。**
                        「何を入れる場所か」を 1 行で書いてください
                        （クラスの一覧を書き写すと、クラスが増えるたびに古くなります）。""")
                .isEmpty();
    }
}
