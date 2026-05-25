package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipperAggregateTest {

    private FixtureConfiguration<Shipper> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Shipper.class);
    }

    @Test
    @DisplayName("US02: 個人荷主を登録できる")
    void 個人荷主を登録できる() {
        // Given: 何もない状態
        // When: 個人荷主登録コマンド
        // Then: ShipperRegisteredEvent が発行される
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-001",
                        ShipperType.INDIVIDUAL,
                        "山田太郎",
                        "東京都千代田区丸の内 1-1",
                        null,
                        "千代田区",
                        "JP",
                        "100-0005",
                        "yamada@example.com",
                        "03-1234-5678"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShipperRegisteredEvent(
                        "S-001",
                        ShipperType.INDIVIDUAL,
                        "山田太郎",
                        "東京都千代田区丸の内 1-1",
                        null,
                        "千代田区",
                        "JP",
                        "100-0005",
                        "yamada@example.com",
                        "03-1234-5678"));
    }

    @Test
    @DisplayName("US02: 荷主 ID が空文字列の場合は例外が発生する")
    void 荷主IDが空文字列の場合は例外が発生する() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "",
                        ShipperType.INDIVIDUAL,
                        "山田太郎",
                        "東京都千代田区丸の内 1-1",
                        null,
                        "千代田区",
                        "JP",
                        "100-0005",
                        "yamada@example.com",
                        "03-1234-5678"))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US02: メールアドレスが空文字列の場合は例外が発生する")
    void メールアドレスが空文字列の場合は例外が発生する() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-002",
                        ShipperType.INDIVIDUAL,
                        "山田太郎",
                        "東京都千代田区丸の内 1-1",
                        null,
                        "千代田区",
                        "JP",
                        "100-0005",
                        "",
                        "03-1234-5678"))
                .expectException(IllegalArgumentException.class);
    }
}
