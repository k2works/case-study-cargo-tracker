package com.example.cargotracker.shared.archunit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 規則そのものが赤を出せることを、違反フィクスチャで固定する。
 *
 * <p>サービス側の適用は、実クラスが入るまで「何も検査しない」状態になりうる。
 * 規則を書いただけで安心しないために、ここで 1 本ずつ壊れた形を食わせて赤を確かめる。
 * フィクスチャは最小の違反例ではなく<b>実コードと同じ形</b>で書く。形が違うと、
 * ここが緑でも実コードの違反を見逃す。</p>
 */
class ArchRulesAreEffectiveTest {

    private static final String FIXTURES = "com.example.cargotracker.archfixture";

    private static JavaClasses violating() {
        return new ClassFileImporter().importPackages(FIXTURES + ".violating");
    }

    private static JavaClasses compliant() {
        return new ClassFileImporter().importPackages(FIXTURES + ".compliant");
    }

    static Stream<Arguments> rules() {
        return Stream.of(
                Arguments.of("ドメインは Spring に依存しない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::domainDoesNotDependOnSpring),
                Arguments.of("ドメインは MyBatis に依存しない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::domainDoesNotDependOnMyBatis),
                Arguments.of("ドメインの Axon は許可リストだけ",
                        (Supplier<ArchRule>) CargoTrackerArchRules::domainUsesOnlyAllowedAxonTypes),
                Arguments.of("投影はコマンドを送らない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::onlyInterfacesAndReactionSendCommands),
                Arguments.of("Saga を使わない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::sagaIsNotUsed),
                Arguments.of("Clock.systemUTC() を直接呼ばない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::doesNotCallSystemUtcClockDirectly),
                Arguments.of("ACL は HTTP を使わない",
                        (Supplier<ArchRule>) CargoTrackerArchRules::aclDoesNotUseHttpClients));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("違反フィクスチャに対して赤を出す")
    void rulesRejectViolations(String name, Supplier<ArchRule> rule) {
        assertThatThrownBy(() -> rule.get().check(violating()))
                .as("%s: 規則が違反を見逃している。書いただけで働いていない", name)
                .isInstanceOf(AssertionError.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("準拠フィクスチャは通す")
    void rulesAcceptCompliantCode(String name, Supplier<ArchRule> rule) {
        assertThatCode(() -> rule.get().allowEmptyShould(true).check(compliant()))
                .as("%s: 正しい書き方まで赤にしている", name)
                .doesNotThrowAnyException();
    }
}
