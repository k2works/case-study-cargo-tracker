package com.example.cargotracker.shared.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 設計の境界を検査する規則。正典は architecture_backend.md「レイヤー責務一覧」と
 * ADR-0001 のコンプライアンス欄。
 *
 * <p>規則は全サービスに同じものを当てる。サービスごとに緩めると、緩めた理由が
 * その場限りになり、境界を引いた動機が消える。</p>
 */
public final class CargoTrackerArchRules {

    private CargoTrackerArchRules() {
    }

    static final String EVENT_APPENDER =
            "org.axonframework.messaging.eventhandling.gateway.EventAppender";

    static final String EVENT_SOURCED_STEREOTYPE =
            "org.axonframework.extension.spring.stereotype.EventSourced";

    public static ArchRule domainDoesNotDependOnSpring() {
        return noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("ドメイン層はフレームワークに依存しない。"
                        + "Spring 由来の型は @EventSourced の 1 つだけを別途許す（ADR-0001 決定 3）");
    }

    public static ArchRule domainDoesNotDependOnMyBatis() {
        return noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.mybatis..", "org.apache.ibatis..")
                .because("ドメイン層は永続化の都合を知らない");
    }

    public static ArchRule domainUsesOnlyAllowedAxonTypes() {
        return noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("許可リストに無い Axon の型") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        String name = javaClass.getName();
                        return name.startsWith("org.axonframework.") && !isAllowedInDomain(name);
                    }
                })
                .because("許可リストに無い Axon の型をドメインが使うと、実行時のフレームワーク呼び出しが"
                        + "ドメインに入り込む（architecture_backend.md）");
    }

    /**
     * ドメインが持ってよい Axon の型か。
     *
     * <p>アノテーション（{@code ..annotation..} 配下）と、イベント発行に要る
     * {@code EventAppender}、集約を Command Bus に登録する {@code @EventSourced} の 3 種だけ。</p>
     */
    static boolean isAllowedInDomain(String className) {
        if (className.contains(".annotation.")) {
            return true;
        }
        return className.equals(EVENT_APPENDER) || className.equals(EVENT_SOURCED_STEREOTYPE);
    }

    /** 投影はコマンドを送らない。送ってよいのは interfaces と application/reaction だけ。 */
    public static ArchRule onlyInterfacesAndReactionSendCommands() {
        return noClasses().that()
                .resideOutsideOfPackages("..interfaces..", "..application.reaction..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.axonframework.messaging.commandhandling.gateway.CommandGateway")
                .because("投影がコマンドを送るとリプレイで副作用が再実行される（ADR-0001）。"
                        + "CommandGateway を使えるのは interfaces と application/reaction の 2 か所だけ");
    }

    /** Saga は使わない（ADR-0001 決定 6）。名簿でなく「その名前があれば赤」にする。 */
    public static ArchRule sagaIsNotUsed() {
        return noClasses().should().resideInAPackage("..application.saga..")
                .because("Axon 5 に Saga は無い。調整役は application/reaction の"
                        + " Reaction Handler で実装する（ADR-0001 決定 6）");
    }

    /** 業務の「今日」は業務タイムゾーンで決める。Clock.systemUTC() の直呼びを禁じる。 */
    public static ArchRule doesNotCallSystemUtcClockDirectly() {
        return noClasses().that().resideOutsideOfPackage("..shared.infrastructure.time..")
                .should().callMethod(java.time.Clock.class, "systemUTC")
                .because("業務日付を UTC で判断すると、時差の分だけ「当日」の受付が拒否される"
                        + "時間帯ができる。BusinessClock を通す");
    }

    /** ACL は HTTP を直接使わない。サービス間の配送経路は Axon Server 一本（ADR-0001 決定 4）。 */
    public static ArchRule aclDoesNotUseHttpClients() {
        return noClasses().that().resideInAPackage("..infrastructure.acl..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("RestTemplate")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("RestClient")
                .because("サービス間の配送経路は Axon Server 一本にする（ADR-0001 決定 4）");
    }
}
