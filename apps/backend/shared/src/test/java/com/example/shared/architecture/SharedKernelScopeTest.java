package com.example.shared.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 共有カーネルが太っていないことを検査する。
 *
 * <p>サービス独立性の検査（{@code serviceIsolationRule}）は shared をまるごと除外している。
 * 除外がある場所は、置きさえすれば全サービスから使えるため放っておくと太る。IT3 で routingms が
 * 地点を使い始めるのを機に、置いてよいものの枠をここで固定する。
 */
@DisplayName("共有カーネルの範囲")
class SharedKernelScopeTest {

    /**
     * 共有カーネルとして配布されるクラスだけを読む。
     *
     * <p>testFixtures（アーキテクチャ規則・方言スモーク）はテストの道具であり、
     * 共有カーネルの一部ではない。同じパッケージ空間にいるため、明示的に外す。
     */
    private static final Path MAIN_CLASSES = Path.of("build/classes/java/main");

    private final JavaClasses classes = importSharedKernel();

    private static JavaClasses importSharedKernel() {
        JavaClasses classes = new ClassFileImporter().importPath(MAIN_CLASSES);
        if (!classes.iterator().hasNext()) {
            throw new IllegalStateException(
                    "共有カーネルのクラスが 1 件も読めていません。この状態では規則が常に緑になります");
        }
        return classes;
    }

    @Test
    @DisplayName("共有カーネルに置けるのは地点と認証契約だけ")
    void staysWithinItsScope() {
        HexagonalArchitectureRules.sharedKernelScopeRule().check(classes);
    }
}
