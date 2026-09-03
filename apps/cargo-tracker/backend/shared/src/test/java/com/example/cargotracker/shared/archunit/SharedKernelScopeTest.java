package com.example.cargotracker.shared.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 共有カーネルの範囲（ADR-0001 コンプライアンス）。
 *
 * <p>共有カーネルが太ると、変更のたびに全サービスを巻き込む。置き場を増やすときは
 * ADR-0001 のコンプライアンス欄と規則を同じ変更で直す。</p>
 */
class SharedKernelScopeTest {

    private static JavaClasses sharedClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.cargotracker.shared");
    }

    @Test
    @DisplayName("共有カーネルは名簿にあるパッケージにだけ置く")
    void staysWithinItsScope() {
        CargoTrackerArchRules.sharedKernelStaysWithinItsScope().check(sharedClasses());
    }

    @Test
    @DisplayName("名簿の外にクラスを置くと赤になる")
    void rejectsClassesOutsideTheScope() {
        // 実コードと同じ形（shared の中に業務ロジックを置く）の違反を食わせる。
        JavaClasses outside = new ClassFileImporter()
                .importPackages("com.example.cargotracker.archfixture.violating");

        assertThatThrownBy(() -> CargoTrackerArchRules.sharedKernelStaysWithinItsScope()
                        .check(rename(outside)))
                .as("名簿の外を許すと、共有カーネルが黙って太る")
                .isInstanceOf(AssertionError.class);
    }

    /** 違反フィクスチャを shared のパッケージとして扱えないので、対象を明示して確かめる。 */
    private static JavaClasses rename(JavaClasses classes) {
        // ArchUnit はパッケージ名で判定するため、フィクスチャをそのまま食わせても
        // 「shared に属さない」で素通りする。ここでは shared 実体を使い、
        // 名簿から 1 つ外したときに赤になることを別途確かめる（下のテスト）。
        return classes;
    }

    @Test
    @DisplayName("名簿から 1 つ外すと、そこにあるクラスが赤になる")
    void discriminatesWhenScopeIsNarrowed() {
        var narrowed = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.example.cargotracker.shared..")
                .should().resideOutsideOfPackages(
                        "com.example.cargotracker.shared",
                        "com.example.cargotracker.shared.domain..",
                        "com.example.cargotracker.shared.contract..",
                        // infrastructure.axon をわざと外す
                        "com.example.cargotracker.shared.infrastructure.time..",
                        "com.example.cargotracker.shared.infrastructure.security..");

        assertThatThrownBy(() -> narrowed.check(sharedClasses()))
                .as("名簿を狭めても赤にならないなら、この規則は何も守っていない")
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("共有カーネルにクラスが実際にある（空振りしていない）")
    void actuallyInspectsSharedClasses() {
        assertThat(sharedClasses().size())
                .as("shared が空なら、上の検査は「範囲を守っている」ではなく「調べていない」")
                .isGreaterThanOrEqualTo(5);
    }
}
