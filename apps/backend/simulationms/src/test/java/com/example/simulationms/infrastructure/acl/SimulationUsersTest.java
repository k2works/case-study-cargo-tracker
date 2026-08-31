package com.example.simulationms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 工程を踏む利用者の名簿（[ADR-030] 決定 2）。
 *
 * <p><strong>載っていないロールを素通りさせない。</strong>素通りさせると、
 * 名簿に載せ忘れたロールの工程が「誰でもない者」として実行される——
 * 認可を確かめるためにこの仕組みを作った意味が消える。
 */
@DisplayName("工程を踏む利用者")
class SimulationUsersTest {

    @Test
    @DisplayName("ロールに対応する利用者を返す")
    void findsTheUserForARole() {
        SimulationUsers users = SimulationUsers.of(Map.of("ROLE_SALES", "sales01"), "password");

        assertThat(users.usernameFor("ROLE_SALES")).isEqualTo("sales01");
        assertThat(users.password()).isEqualTo("password");
    }

    @Test
    @DisplayName("名簿に無いロールは、名前を挙げて断る")
    void rejectsAnUnknownRole() {
        SimulationUsers users = SimulationUsers.of(Map.of("ROLE_SALES", "sales01"), "password");

        assertThatThrownBy(() -> users.usernameFor("ROLE_ACCOUNTANT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_ACCOUNTANT");
    }

    @Test
    @DisplayName("空の名簿では作れない")
    void requiresAtLeastOneUser() {
        assertThatThrownBy(() -> SimulationUsers.of(Map.of(), "password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("合言葉の無い名簿では作れない")
    void requiresAPassword() {
        assertThatThrownBy(() -> SimulationUsers.of(Map.of("ROLE_SALES", "sales01"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
