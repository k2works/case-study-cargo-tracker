package com.example.cargotracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.archunit.CargoTrackerArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * authms は Event Sourcing にしない（ADR-0001 決定 2）。
 *
 * <p>履歴が業務上も学習上も要らないので状態保存にする。集約を足すと、
 * 「なぜ authms だけ ES でないのか」の判断が静かに崩れる。</p>
 */
class AuthIsNotEventSourcedTest {

    private static JavaClasses authClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.cargotracker.auth");
    }

    @Test
    @DisplayName("authms に @EventSourced の集約が無い")
    void hasNoEventSourcedAggregate() {
        CargoTrackerArchRules.authIsNotEventSourced().allowEmptyShould(true).check(authClasses());
    }

    @Test
    @DisplayName("authms にクラスが実際にある（空振りしていない）")
    void actuallyInspectsAuthClasses() {
        assertThat(authClasses().size())
                .as("authms が空なら、上の検査は「ES にしていない」ではなく「調べていない」")
                .isGreaterThanOrEqualTo(5);
    }
}
