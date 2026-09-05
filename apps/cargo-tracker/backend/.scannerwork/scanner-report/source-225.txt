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

    // 「名簿の外にクラスを置くと赤になる」という検査は置かない。
    //
    // ArchUnit はパッケージ名で判定するので、`shared` の外にあるフィクスチャを
    // 食わせても対象 0 件になる。以前ここに置いていたテストは、違反を検出した
    // のではなく **対象 0 件で ArchUnit が投げた例外**を捕まえていた
    // （`allowEmptyShould(false)` の既定）。緑だが何も判別していない。
    //
    // 判別できるのは下の `discriminatesWhenScopeIsNarrowed` である。実際の
    // `shared` に対して名簿を狭め、そこにあるクラスが赤になることを見る。

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
