package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class ShipperAggregateTest {

    private FixtureConfiguration<Shipper> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Shipper.class);
    }

    @Test
    @DisplayName("US02: 個人荷主を登録できる")
    void 個人荷主を登録できる() {
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
                        "03-1234-5678",
                        null,
                        null))
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
                        "03-1234-5678",
                        null,
                        null));
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
                        "03-1234-5678",
                        null,
                        null))
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
                        "03-1234-5678",
                        null,
                        null))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US03: 法人荷主を契約番号と割引率付きで登録できる")
    void 法人荷主を契約番号と割引率付きで登録できる() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-100",
                        ShipperType.CORPORATE,
                        "株式会社グローバル商事",
                        "東京都港区六本木 6-10-1",
                        "ミッドタウンタワー 30F",
                        "港区",
                        "JP",
                        "106-6130",
                        "biz@global.example.com",
                        "03-5555-0001",
                        "CONTRACT-2026-001",
                        new BigDecimal("0.150")))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShipperRegisteredEvent(
                        "S-100",
                        ShipperType.CORPORATE,
                        "株式会社グローバル商事",
                        "東京都港区六本木 6-10-1",
                        "ミッドタウンタワー 30F",
                        "港区",
                        "JP",
                        "106-6130",
                        "biz@global.example.com",
                        "03-5555-0001",
                        "CONTRACT-2026-001",
                        new BigDecimal("0.150")));
    }

    @Test
    @DisplayName("US03: 法人荷主登録時に契約番号が未指定なら例外")
    void 法人荷主登録時に契約番号が未指定なら例外() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-101",
                        ShipperType.CORPORATE,
                        "株式会社A",
                        "東京",
                        null,
                        "千代田区",
                        "JP",
                        "100-0001",
                        "a@example.com",
                        "03-1111-1111",
                        null,
                        new BigDecimal("0.100")))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US03: 法人荷主登録時に割引率が未指定なら例外")
    void 法人荷主登録時に割引率が未指定なら例外() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-102",
                        ShipperType.CORPORATE,
                        "株式会社B",
                        "東京",
                        null,
                        "千代田区",
                        "JP",
                        "100-0001",
                        "b@example.com",
                        "03-1111-1112",
                        "CONTRACT-B",
                        null))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US03: 割引率が 0.3 を超える場合は例外")
    void 割引率が30パーセントを超える場合は例外() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-103",
                        ShipperType.CORPORATE,
                        "株式会社C",
                        "東京",
                        null,
                        "千代田区",
                        "JP",
                        "100-0001",
                        "c@example.com",
                        "03-1111-1113",
                        "CONTRACT-C",
                        new BigDecimal("0.301")))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US03: 割引率が負数の場合は例外")
    void 割引率が負数の場合は例外() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-104",
                        ShipperType.CORPORATE,
                        "株式会社D",
                        "東京",
                        null,
                        "千代田区",
                        "JP",
                        "100-0001",
                        "d@example.com",
                        "03-1111-1114",
                        "CONTRACT-D",
                        new BigDecimal("-0.001")))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US03: 個人荷主に契約情報を渡すと例外")
    void 個人荷主に契約情報を渡すと例外() {
        fixture.givenNoPriorActivity()
                .when(new RegisterShipperCommand(
                        "S-105",
                        ShipperType.INDIVIDUAL,
                        "佐藤花子",
                        "東京",
                        null,
                        "千代田区",
                        "JP",
                        "100-0001",
                        "sato@example.com",
                        "03-1111-1115",
                        "CONTRACT-X",
                        new BigDecimal("0.100")))
                .expectException(IllegalArgumentException.class);
    }
}
