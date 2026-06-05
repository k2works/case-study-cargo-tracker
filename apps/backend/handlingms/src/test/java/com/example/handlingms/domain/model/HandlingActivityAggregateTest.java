package com.example.handlingms.domain.model;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.events.UnexpectedHandlingDetectedEvent;
import com.example.handlingms.domain.services.HandlingValidationService;
import com.example.shared.events.HandlingActivityRegisteredEvent.ClaimVerificationData;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * {@link HandlingActivity} 集約の Axon Test Fixture テスト（US15・US16 / IT5 3.2）。
 *
 * <p>受領 / 積込 / 荷降し / 引取 / 税関通過 の 5 種別と、その不変条件
 * （LOAD/UNLOAD で航海番号必須、CLAIM で荷受人確認必須、未来時刻拒否、
 * 重複登録拒否、予定外場所の警告イベント）を Given-When-Then で担保する。</p>
 */
class HandlingActivityAggregateTest {

    private FixtureConfiguration<HandlingActivity> fixture;
    private HandlingValidationService validationService;
    // テスト時刻はシステム時刻より過去を使う（未来時刻拒否の不変条件を回避）
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 3, 3, 10, 0);
    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 3, 28, 17, 30);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(HandlingActivity.class);
        // バリデーション用ドメインサービスは Mock 化し、デフォルトは「重複なし・予定通り」。
        validationService = Mockito.mock(HandlingValidationService.class);
        Mockito.when(validationService.hasDuplicate(
                Mockito.anyString(), Mockito.any(HandlingType.class),
                Mockito.anyString(), Mockito.any(LocalDateTime.class)))
                .thenReturn(false);
        Mockito.when(validationService.detectUnexpected(
                Mockito.anyString(), Mockito.any(HandlingType.class), Mockito.anyString()))
                .thenReturn(Optional.empty());
        fixture.registerInjectableResource(validationService);
    }

    /**
     * IT8 T1.10: 集約発火型に伴い shared cross-service event も連続 apply される。
     * 旧 outbound publisher（HandlingActivityCrossServicePublisher）を廃止。
     */
    private com.example.shared.events.HandlingActivityRegisteredEvent sharedEvent(
            String activityId, String trackingNumber, HandlingType type,
            LocalDateTime occurredAt, String unlocode, String voyageNumber,
            String handlerId, ClaimVerification verification) {
        ClaimVerificationData data = verification == null ? null
                : new ClaimVerificationData(
                        verification.consigneeName(),
                        verification.signatureRef(),
                        verification.confirmationCode(),
                        verification.verifiedAt());
        return new com.example.shared.events.HandlingActivityRegisteredEvent(
                activityId, trackingNumber, type.name(),
                occurredAt, unlocode, voyageNumber, handlerId, data, false);
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
                .expectEvents(
                        new HandlingActivityRegisteredEvent(
                                "HA-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                                OCCURRED_AT, "JPTYO", null, "OP-001", null),
                        sharedEvent("HA-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
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
                .expectEvents(
                        new HandlingActivityRegisteredEvent(
                                "HA-003", "TRK-AB12CD3456", HandlingType.LOAD,
                                OCCURRED_AT, "JPTYO", "V-100", "OP-001", null),
                        sharedEvent("HA-003", "TRK-AB12CD3456", HandlingType.LOAD,
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
                .expectEvents(
                        new HandlingActivityRegisteredEvent(
                                "HA-006", "TRK-AB12CD3456", HandlingType.CLAIM,
                                OCCURRED_AT, "USNYC", null, "OP-002", verification),
                        sharedEvent("HA-006", "TRK-AB12CD3456", HandlingType.CLAIM,
                                OCCURRED_AT, "USNYC", null, "OP-002", verification));
    }

    // --- IT5 タスク 3.2 ---

    @Test
    @DisplayName("IT5 3.2: 未来時刻の occurredAt は拒否される")
    void 未来時刻は拒否される() {
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-FUT", "TRK-AB12CD3456", HandlingType.RECEIVE,
                LocalDateTime.now().plusDays(1), "JPTYO", null, "OP-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IT5 3.2: 重複登録は IllegalStateException で拒否される")
    void 重複登録は拒否される() {
        Mockito.when(validationService.hasDuplicate(
                Mockito.eq("TRK-AB12CD3456"), Mockito.eq(HandlingType.RECEIVE),
                Mockito.eq("JPTYO"), Mockito.any(LocalDateTime.class)))
                .thenReturn(true);

        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-DUP", "TRK-AB12CD3456", HandlingType.RECEIVE,
                OCCURRED_AT, "JPTYO", null, "OP-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalStateException.class)
                .expectExceptionMessage(org.hamcrest.Matchers.containsString("重複"));
    }

    @Test
    @DisplayName("IT5 3.2: 予定外検知時は HandlingActivityRegisteredEvent + UnexpectedHandlingDetectedEvent の 2 件発行")
    void 予定外検知で警告イベント発行() {
        Mockito.when(validationService.detectUnexpected(
                Mockito.eq("TRK-AB12CD3456"), Mockito.eq(HandlingType.CLAIM),
                Mockito.eq("CNHKG")))
                .thenReturn(Optional.of("CLAIM が destination (USNYC) と異なる場所 (CNHKG) で発生"));

        ClaimVerification verification = new ClaimVerification(
                "山田太郎", null, "A1B2C3", VERIFIED_AT);
        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                "HA-UNEXP", "TRK-AB12CD3456", HandlingType.CLAIM,
                OCCURRED_AT, "CNHKG", null, "OP-003", verification);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new HandlingActivityRegisteredEvent(
                                "HA-UNEXP", "TRK-AB12CD3456", HandlingType.CLAIM,
                                OCCURRED_AT, "CNHKG", null, "OP-003", verification),
                        sharedEvent("HA-UNEXP", "TRK-AB12CD3456", HandlingType.CLAIM,
                                OCCURRED_AT, "CNHKG", null, "OP-003", verification),
                        new UnexpectedHandlingDetectedEvent(
                                "HA-UNEXP", "TRK-AB12CD3456", HandlingType.CLAIM,
                                "CNHKG", OCCURRED_AT,
                                "CLAIM が destination (USNYC) と異なる場所 (CNHKG) で発生"));
    }
}
