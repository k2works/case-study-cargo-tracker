package com.example.cargotracker.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ヘキサゴナルアーキテクチャの依存関係ルールを検証するアーキテクチャテスト。
 * <p>
 * docs/design/tech_stack.md の「ArchUnit 最低限の検証ルール」に基づく。
 */
@AnalyzeClasses(packages = "com.example.cargotracker")
class ArchitectureTest {

    /**
     * A01: ドメイン層がインフラ層に依存しないこと。
     * domain パッケージは infrastructure パッケージを import してはならない。
     */
    @ArchTest
    static final ArchRule A01_ドメイン層はインフラ層に依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("[A01] ドメイン層はインフラ層に依存してはならない");

    /**
     * A02: ドメイン層に Spring アノテーションを使用しないこと。
     * @Component, @Service, @Repository 等は infrastructure/application 層にのみ使用する。
     */
    @ArchTest
    static final ArchRule A02_ドメイン層にSpringアノテーションを使用しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.context.annotation..",
                            "org.springframework.beans.factory.annotation.."
                    )
                    .as("[A02] ドメイン層は Spring ステレオタイプアノテーション（@Component, @Service, @Repository 等）を使用してはならない");

    /**
     * A03: アプリケーション層がインフラ層を直接参照しないこと。
     * application パッケージから infrastructure パッケージへの直接依存は禁止。Port 経由で参照する。
     */
    @ArchTest
    static final ArchRule A03_アプリケーション層はインフラ層を直接参照しない =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("[A03] アプリケーション層はインフラ層を直接参照してはならない（Port 経由のみ）");

    /**
     * A04: booking の domain/application 層が shipper コンテキストを直接参照しないこと。
     * booking.infrastructure 層は ACL アダプターとして shipper への参照を許可する。
     */
    @ArchTest
    static final ArchRule A04_bookingのドメイン_アプリケーション層はshipperを直接参照しない =
            noClasses()
                    .that().resideInAPackage("com.example.cargotracker.booking.domain..")
                    .or().resideInAPackage("com.example.cargotracker.booking.application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.shipper..")
                    .as("[A04] booking の domain/application 層は shipper コンテキストを直接参照してはならない（ACL/Event 経由のみ）");

    /**
     * A04b: shipper コンテキストは booking コンテキストのクラスを直接参照してはならない。
     */
    @ArchTest
    static final ArchRule A04b_shipperはbookingを直接参照しない =
            noClasses()
                    .that().resideInAPackage("com.example.cargotracker.shipper..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.booking..")
                    .as("[A04b] shipper コンテキストは booking コンテキストのクラスを直接参照してはならない（ACL/Event 経由のみ）");
}
