package com.example.handlingms.domain.model;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * {@link HandlingActivity} 集約の Axon Test Fixture テスト（US15・US16 / IT5 3.2）。
 *
 * <p>受領 / 積込 / 荷降し / 引取 / 税関通過 の 5 種別と、その不変条件
 * （LOAD/UNLOAD で航海番号必須、CLAIM で荷受人確認必須）を Given-When-Then で担保する。</p>
 */
class HandlingActivityAggregateTest {

    private FixtureConfiguration<HandlingActivity> fixture;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 3, 10, 0);
    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 7, 28, 17, 30);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(HandlingActivity.class);
    }

    @Test
    @DisplayName("US15: 受領（RECEIVE）を航海番号なしで登録できる")
    void 受領作業を登録できる() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                OCCURRED_AT, "JPTYO", null, "OP-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new HandlingActivityRegisteredEvent(
                        "HA-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                        OCCURRED_AT, "JPTYO", null, "OP-001", null));
    }

    @Test
    @DisplayName("US15: 積込（LOAD）は航海番号必須")
    void 積込作業は航海番号必須() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-002", "TRK-AB12CD3456", HandlingType.LOAD,
                OCCURRED_AT, "JPTYO", null, "OP-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US15: 積込（LOAD）に航海番号を付与すれば登録できる")
    void 積込作業を航海番号付きで登録できる() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-003", "TRK-AB12CD3456", HandlingType.LOAD,
                OCCURRED_AT, "JPTYO", "V-100", "OP-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new HandlingActivityRegisteredEvent(
                        "HA-003", "TRK-AB12CD3456", HandlingType.LOAD,
                        OCCURRED_AT, "JPTYO", "V-100", "OP-001", null));
    }

    @Test
    @DisplayName("US15: 荷降し（UNLOAD）も航海番号必須")
    void 荷降し作業は航海番号必須() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-004", "TRK-AB12CD3456", HandlingType.UNLOAD,
                OCCURRED_AT, "USNYC", null, "OP-002", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US16: 引取（CLAIM）は荷受人確認必須")
    void 引取作業は荷受人確認必須() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-005", "TRK-AB12CD3456", HandlingType.CLAIM,
                OCCURRED_AT, "USNYC", null, "OP-002", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US16: 引取（CLAIM）に荷受人確認（署名）を付与すれば登録できる")
    void 引取作業を署名付きで登録できる() {
        ClaimVerification verification = new ClaimVerification(
                "山田太郎", "sig://abc", null, VERIFIED_AT);
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-006", "TRK-AB12CD3456", HandlingType.CLAIM,
                OCCURRED_AT, "USNYC", null, "OP-002", verification);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new HandlingActivityRegisteredEvent(
                        "HA-006", "TRK-AB12CD3456", HandlingType.CLAIM,
                        OCCURRED_AT, "USNYC", null, "OP-002", verification));
    }
}
