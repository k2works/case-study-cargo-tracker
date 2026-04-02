package com.example.cargotracker.arch;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.example.cargotracker")
class ArchitectureTest {

    // A01: ドメイン層（aggregates/entities/valueobjects）はアプリ/インフラ/インターフェース層に依存しない
    @ArchTest
    static final ArchRule A01_ドメイン層はアプリケーション_インフラ_インターフェース層に依存しない =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model.aggregates..",
                            "..domain.model.entities..",
                            "..domain.model.valueobjects.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application.internal..",
                            "..infrastructure..",
                            "..interfaces.."
                    )
                    .as("[A01] ドメイン層（aggregates/entities/valueobjects）はアプリケーション層・インフラ層・インターフェース層のクラスに依存してはならない");

    // A02: ドメイン層に Spring の stereotype アノテーション（@Service, @Component, @Repository）を使用しない
    @ArchTest
    static final ArchRule A02_ドメイン層にSpringアノテーションを使用しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                    .orShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
                    .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class)
                    .as("[A02] ドメイン層のクラスに Spring の stereotype アノテーションを使用してはならない");

    // A03: application.internal.commandservices / queryservices はインフラ層を直接参照しない
    //      （outboundservices ポート経由のみ許可）
    @ArchTest
    static final ArchRule A03_コマンド_クエリサービスはインフラ層を直接参照しない =
            noClasses()
                    .that().resideInAnyPackage(
                            "..application.internal.commandservices..",
                            "..application.internal.queryservices.."
                    )
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("[A03] commandservices/queryservices はインフラ層を直接参照してはならない（outboundservices ポート経由のみ）");

    // A04: interfaces.rest はインフラ層を直接参照しない
    @ArchTest
    static final ArchRule A04_インターフェース層はインフラ層を直接参照しない =
            noClasses()
                    .that().resideInAPackage("..interfaces.rest..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("[A04] interfaces.rest はインフラ層（infrastructure）を直接参照してはならない");

    // A05: booking の domain/application.internal（commandservices/queryservices）は shipper を直接参照しない
    @ArchTest
    static final ArchRule A05_bookingのドメイン_サービス層はshipperを直接参照しない =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.example.cargotracker.booking.domain..",
                            "com.example.cargotracker.booking.application.internal.commandservices..",
                            "com.example.cargotracker.booking.application.internal.queryservices.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.shipper..")
                    .as("[A05] booking の domain/commandservices/queryservices は shipper コンテキストを直接参照してはならない（ACL 経由のみ）");

    // A05b: shipper は booking を直接参照しない
    @ArchTest
    static final ArchRule A05b_shipperはbookingを直接参照しない =
            noClasses()
                    .that().resideInAPackage("com.example.cargotracker.shipper..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.booking..")
                    .as("[A05b] shipper コンテキストは booking コンテキストのクラスを直接参照してはならない（ACL/Event 経由のみ）");

    // A06: routing の domain/application 層は booking を直接参照しない
    //      （BookingQueryPort インターフェース経由のみ許可）
    @ArchTest
    static final ArchRule A06_routingのドメイン_サービス層はbookingを直接参照しない =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.example.cargotracker.routing.domain..",
                            "com.example.cargotracker.routing.application.internal.commandservices..",
                            "com.example.cargotracker.routing.application.internal.queryservices.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.booking..")
                    .as("[A06] routing の domain/commandservices/queryservices は booking コンテキストを直接参照してはならない（BookingQueryPort 経由のみ）");

    // A07: quote の domain 層は routing/booking を直接参照しない
    @ArchTest
    static final ArchRule A07_quoteのドメイン層はrouting_bookingを直接参照しない =
            noClasses()
                    .that().resideInAPackage("com.example.cargotracker.quote.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.example.cargotracker.routing..",
                            "com.example.cargotracker.booking.."
                    )
                    .as("[A07] quote の domain 層は routing・booking コンテキストのクラスを直接参照してはならない");

    // A08: ADR-002 に従い、ドメインイベントリスナーで @EventListener を使用しない
    @ArchTest
    static final ArchRule A08_ドメインイベントリスナーでEventListenerを使用しない =
            methods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..application..",
                            "..infrastructure.."
                    )
                    .should().notBeAnnotatedWith(EventListener.class)
                    .as("[A08] application/infrastructure のドメインイベントリスナーに @EventListener を使用してはならない");

    // A09: ADR-002 に従い、ドメインイベントリスナーは AFTER_COMMIT を使用する
    @ArchTest
    static final ArchRule A09_ドメインイベントリスナーはAfterCommitで実行する =
            methods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..application..",
                            "..infrastructure.."
                    )
                    .and().areAnnotatedWith(TransactionalEventListener.class)
                    .should(haveAfterCommitTransactionalEventListener())
                    .as("[A09] application/infrastructure のドメインイベントリスナーは @TransactionalEventListener(AFTER_COMMIT) を使用しなければならない");

    // A10: tracking の application.internal は handling の domain を直接参照しない
    @ArchTest
    static final ArchRule A10_trackingのアプリケーション層はhandlingのドメインを直接参照しない =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.example.cargotracker.tracking.application.internal..",
                            "com.example.cargotracker.tracking.domain.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.cargotracker.handling.domain..")
                    .as("[A10] tracking の application/domain 層は handling の domain 層を直接参照してはならない（ACL 経由のみ）");

    private static ArchCondition<JavaMethod> haveAfterCommitTransactionalEventListener() {
        return new ArchCondition<>("use @TransactionalEventListener(phase = AFTER_COMMIT)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                TransactionalEventListener annotation =
                        method.reflect().getAnnotation(TransactionalEventListener.class);
                boolean isAfterCommit = annotation != null
                        && annotation.phase() == TransactionPhase.AFTER_COMMIT;
                String message = method.getFullName()
                        + " は @TransactionalEventListener(phase = AFTER_COMMIT) でなければなりません";
                events.add(new SimpleConditionEvent(method, isAfterCommit, message));
            }
        };
    }
}
