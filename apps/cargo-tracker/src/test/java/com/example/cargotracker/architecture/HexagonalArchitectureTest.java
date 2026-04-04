package com.example.cargotracker.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ヘキサゴナルアーキテクチャの依存関係ルールを自動検証する。
 * ADR-011 に基づく。
 */
@AnalyzeClasses(
        packages = "com.example.cargotracker",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    // ルール 1: ドメイン層はインフラ層に依存しない
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);

    // ルール 2: ドメイン層はインターフェース層に依存しない
    @ArchTest
    static final ArchRule domain_should_not_depend_on_interfaces =
            noClasses().that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                    .allowEmptyShould(true);

    // ルール 3: ドメイン層に Spring アノテーションを使用しない
    @ArchTest
    static final ArchRule domain_should_not_use_spring_component =
            noClasses().that().resideInAPackage("..domain.model..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Component")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_should_not_use_spring_service =
            noClasses().that().resideInAPackage("..domain.model..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_should_not_use_spring_repository =
            noClasses().that().resideInAPackage("..domain.model..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .allowEmptyShould(true);

    // ルール 4: アプリケーション層はインターフェース層に依存しない
    @ArchTest
    static final ArchRule application_should_not_depend_on_interfaces =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                    .allowEmptyShould(true);
}
