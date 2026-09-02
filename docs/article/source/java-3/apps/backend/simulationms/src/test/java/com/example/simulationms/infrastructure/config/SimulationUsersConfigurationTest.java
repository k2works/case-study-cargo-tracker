package com.example.simulationms.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.infrastructure.acl.SimulationUsers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * すべての工程に、それを踏む利用者が設定されている（[ADR-030] 決定 2）。
 *
 * <p><strong>工程の一覧から回す。</strong>ロールを書き並べると、工程を足したときに
 * 書き足し忘れたロールだけが無検査で残る——その工程は実行して初めて落ちる。
 */
@SpringBootTest
@DisplayName("工程を踏む利用者の設定")
class SimulationUsersConfigurationTest {

    @Autowired
    private SimulationUsers users;

    @ParameterizedTest
    @EnumSource(ScenarioStep.class)
    @DisplayName("どの工程にも、それを踏むロールの利用者が居る")
    void hasAUserForEveryStep(ScenarioStep step) {
        assertThatCode(() -> users.usernameFor(step.role()))
                .as("%s（%s）を踏む利用者が設定されていない", step.label(), step.role())
                .doesNotThrowAnyException();

        assertThat(users.usernameFor(step.role())).isNotBlank();
    }
}
