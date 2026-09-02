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

    /**
     * testFixtures にも枠を置く。
     *
     * <p>本番側だけを見ていると、**testFixtures が野放しになる**。
     * {@code serviceIsolationRule} は {@code com.example.shared} をまるごと除外しているため、
     * testFixtures も全サービスから使える。便利なテストユーティリティ・共通のテストデータ
     * ビルダ・BC をまたぐ DTO が流入しても、誰も落ちない。
     *
     * <p>IT7 で契約（{@code contract}）を testFixtures に置くと決めた以上、その決定にも
     * 検査を用意する。<strong>置いてよいものを列挙する</strong>形にするのは本番側と同じ理由で、
     * 「置いてはいけないもの」を挙げると思いつかなかったものが素通りするためである。
     */
    @Test
    @DisplayName("testFixtures に置けるのは、規則と契約だけ")
    void testFixturesStayWithinTheirScope() {
        JavaClasses fixtures = new ClassFileImporter()
                .importPath(Path.of("build/classes/java/testFixtures"));
        if (!fixtures.iterator().hasNext()) {
            throw new IllegalStateException(
                    "testFixtures のクラスが 1 件も読めていません。この状態では規則が常に緑になります");
        }

        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .classes().that().resideInAPackage("com.example.shared..")
                .should().resideInAnyPackage(
                        "com.example.shared.architecture",
                        "com.example.shared.contract")
                .as("testFixtures に置けるのは、全サービスに配る規則と、"
                        + "サービス間で合意した契約だけ（新しい種類を足すなら ADR で決める）")
                .check(fixtures);
    }
}
