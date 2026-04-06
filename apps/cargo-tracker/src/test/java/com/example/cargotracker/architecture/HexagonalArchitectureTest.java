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

    // ルール 5: Booking のドメイン層・アプリケーション層は Shipper コンテキストのドメイン層に直接依存しない（shared 経由のみ）
    // Infrastructure 層の ACL アダプター（ShipperExistenceCheckerAdapter）は許容する
    @ArchTest
    static final ArchRule booking_domain_should_not_depend_on_shipper_domain =
            noClasses().that().resideInAPackage("..booking.domain..")
                    .should().dependOnClassesThat().resideInAPackage("..shipper.domain..")
                    .as("Booking ドメイン層は Shipper コンテキストのドメイン層に直接依存してはならない（shared 経由のみ許可）");

    @ArchTest
    static final ArchRule booking_application_should_not_depend_on_shipper_domain =
            noClasses().that().resideInAPackage("..booking.application..")
                    .should().dependOnClassesThat().resideInAPackage("..shipper.domain..")
                    .as("Booking アプリケーション層は Shipper コンテキストのドメイン層に直接依存してはならない（ACL ポート経由のみ許可）");

    // ルール 6: Booking コンテキストは Shipper コンテキストの application/infrastructure に依存しない
    @ArchTest
    static final ArchRule booking_should_not_depend_on_shipper_application =
            noClasses().that().resideInAPackage("..booking..")
                    .and().resideOutsideOfPackage("..booking.interfaces.web..")
                    .should().dependOnClassesThat().resideInAPackage("..shipper.application..")
                    .as("Booking コンテキスト（Web 層除く）は Shipper コンテキストの Application 層に依存してはならない");

    @ArchTest
    static final ArchRule booking_should_not_depend_on_shipper_infrastructure =
            noClasses().that().resideInAPackage("..booking..")
                    .should().dependOnClassesThat().resideInAPackage("..shipper.infrastructure..")
                    .as("Booking コンテキストは Shipper コンテキストの Infrastructure 層に依存してはならない");
}
