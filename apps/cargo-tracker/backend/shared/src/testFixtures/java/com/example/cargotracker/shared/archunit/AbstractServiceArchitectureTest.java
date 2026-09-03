package com.example.cargotracker.shared.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 各サービスが継承して境界の規則を適用する基底クラス。
 *
 * <p>サービス側は空のサブクラスを 1 つ置くだけでよい。規則を選べるようにすると、
 * 「このサービスだけ外す」が積み上がって境界が形骸化する。</p>
 */
public abstract class AbstractServiceArchitectureTest {

    private static JavaClasses classes;

    /** 検査対象のパッケージ。サービスごとに {@code com.example.cargotracker.<service>} を返す。 */
    protected abstract String servicePackage();

    @BeforeAll
    static void resetCache() {
        classes = null;
    }

    /**
     * サービスにまだ実クラスが無い層があるあいだ、規則は「何も検査しない」状態になる。
     * ArchUnit は既定でそれを赤にするが、ここでは空を許す。代わりに規則そのものが
     * 赤を出せることを {@code ArchRulesAreEffectiveTest} が違反フィクスチャで固定し、
     * 「どのサービスも空のまま」にならないことを {@code ArchRulesAreAppliedTest} が見る。
     * この 2 本が無いと、空振りが黙って通る。
     */
    private static com.tngtech.archunit.lang.ArchRule allowEmpty(
            com.tngtech.archunit.lang.ArchRule rule) {
        return rule.allowEmptyShould(true);
    }

    private JavaClasses classes() {
        if (classes == null) {
            classes = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(servicePackage());
        }
        return classes;
    }

    @Test
    @DisplayName("ドメイン層は Spring に依存しない")
    void domainDoesNotDependOnSpring() {
        allowEmpty(CargoTrackerArchRules.domainDoesNotDependOnSpring()).check(classes());
    }

    @Test
    @DisplayName("ドメイン層は MyBatis に依存しない")
    void domainDoesNotDependOnMyBatis() {
        allowEmpty(CargoTrackerArchRules.domainDoesNotDependOnMyBatis()).check(classes());
    }

    @Test
    @DisplayName("ドメイン層が使う Axon の型は許可リストの 3 種だけ")
    void domainUsesOnlyAllowedAxonTypes() {
        allowEmpty(CargoTrackerArchRules.domainUsesOnlyAllowedAxonTypes()).check(classes());
    }

    @Test
    @DisplayName("CommandGateway を使えるのは interfaces と application/reaction だけ")
    void onlyInterfacesAndReactionSendCommands() {
        allowEmpty(CargoTrackerArchRules.onlyInterfacesAndReactionSendCommands()).check(classes());
    }

    @Test
    @DisplayName("Saga は使わない（ADR-0001 決定 6）")
    void sagaIsNotUsed() {
        allowEmpty(CargoTrackerArchRules.sagaIsNotUsed()).check(classes());
    }

    @Test
    @DisplayName("Clock.systemUTC() を直接呼ばない")
    void doesNotCallSystemUtcClockDirectly() {
        allowEmpty(CargoTrackerArchRules.doesNotCallSystemUtcClockDirectly()).check(classes());
    }

    @Test
    @DisplayName("ACL は HTTP クライアントを使わない")
    void aclDoesNotUseHttpClients() {
        allowEmpty(CargoTrackerArchRules.aclDoesNotUseHttpClients()).check(classes());
    }

    @Test
    @DisplayName("Reaction Handler は同期クエリを呼ばない")
    void reactionDoesNotCallQueryGateway() {
        allowEmpty(CargoTrackerArchRules.reactionDoesNotCallQueryGateway()).check(classes());
    }

    @Test
    @DisplayName("Saga の型に依存しない（ADR-0001 決定 6）")
    void doesNotDependOnAxonSaga() {
        allowEmpty(CargoTrackerArchRules.doesNotDependOnAxonSaga()).check(classes());
    }
}
