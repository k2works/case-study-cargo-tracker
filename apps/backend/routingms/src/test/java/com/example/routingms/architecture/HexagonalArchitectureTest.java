package com.example.routingms.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ヘキサゴナルアーキテクチャの依存関係ルールを検証する ArchUnit テスト
 *
 * <pre>
 * 依存方向のルール:
 *   domain        ← application ← interfaces (web)
 *   domain        ← infrastructure
 *   domain は infrastructure / interfaces に依存してはならない
 *   application は infrastructure / interfaces に依存してはならない
 * </pre>
 */
@DisplayName("ヘキサゴナルアーキテクチャ依存関係テスト")
class HexagonalArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.routingms";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    @DisplayName("domain 層は infrastructure 層に依存しないこと")
    void domainShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".infrastructure..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("domain 層は interfaces 層に依存しないこと")
    void domainShouldNotDependOnInterfaces() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".interfaces..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("application 層は infrastructure 層に依存しないこと")
    void applicationShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".infrastructure..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("application 層は interfaces 層に依存しないこと")
    void applicationShouldNotDependOnInterfaces() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".interfaces..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
