package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.math.BigDecimal;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Shipper 集約の不変条件（domain-model.md「Shipper 集約」）。 */
class ShipperTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Shipper.class));
        // 集約の単体テストは Axon Server を使わない（繋ぐと発行イベントが記録されない）。
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static RegisterShipperCommand individual() {
        return new RegisterShipperCommand("SHP-000001", "山田太郎", ShipperType.INDIVIDUAL,
                new Email("yamada@example.com"), "03-0000-0000", "東京都港区", null);
    }

    private static CorporateContract contract() {
        return new CorporateContract("CT-0001", new DiscountRate(new BigDecimal("0.1000")));
    }

    @Test
    @DisplayName("個人の荷主を登録すると ShipperRegisteredEvent が出る")
    void registersIndividual() {
        fixture.given().noPriorActivity()
                .when().command(individual())
                .then().success()
                .events(new ShipperRegisteredEvent("SHP-000001", "INDIVIDUAL", "山田太郎",
                        "yamada@example.com", "03-0000-0000", "東京都港区", null, null));
    }

    @Test
    @DisplayName("法人は契約番号と割引率がイベントに載る")
    void registersCorporateWithContract() {
        RegisterShipperCommand command = new RegisterShipperCommand("SHP-000002", "山田商事",
                ShipperType.CORPORATE, new Email("sales@example.com"), "03-1111-1111",
                "東京都中央区", contract());

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().success()
                .events(new ShipperRegisteredEvent("SHP-000002", "CORPORATE", "山田商事",
                        "sales@example.com", "03-1111-1111", "東京都中央区", "CT-0001", "0.1000"));
    }

    @Test
    @DisplayName("法人なのに契約番号が無ければ受け付けない")
    void rejectsCorporateWithoutContract() {
        RegisterShipperCommand command = new RegisterShipperCommand("SHP-000003", "山田商事",
                ShipperType.CORPORATE, new Email("sales@example.com"), "03-1111-1111",
                "東京都中央区", null);

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().exception(IllegalArgumentException.class, "法人は契約番号が必須です");
    }

    @Test
    @DisplayName("個人が法人契約を持っていれば受け付けない")
    void rejectsIndividualWithContract() {
        RegisterShipperCommand command = new RegisterShipperCommand("SHP-000004", "山田太郎",
                ShipperType.INDIVIDUAL, new Email("yamada@example.com"), "03-0000-0000",
                "東京都港区", contract());

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().exception(IllegalArgumentException.class, "個人は法人契約を持てません");
    }

    @Test
    @DisplayName("鍵を破棄したあとでも集約を復元できる（個人情報が null で届く）")
    void restoresAfterShredding() {
        // ADR-0003。鍵を破棄すると Converter が個人情報に null を渡す。復元で検査すると、
        // 削除要求に応えた荷主への後続コマンドがすべて失敗する。
        //
        // AxonTestFixture の given().event() では確かめられない。IT1 の唯一のコマンドは
        // 生成系（static）で、集約を読み込まないため復元経路を通らないため。
        // 更新系コマンドが入るまでは、復元そのものを直接呼んで固定する。
        ShipperRegisteredEvent shredded = new ShipperRegisteredEvent(
                "SHP-000009", "CORPORATE", null, null, null, null, "CT-0001", "0.1000");
        Shipper shipper = new Shipper();

        assertThatCode(() -> shipper.on(shredded))
                .as("復元で例外が出ると、削除済み荷主への後続コマンドがすべて失敗する")
                .doesNotThrowAnyException();
        assertThat(shipper.isShredded()).isTrue();
    }

    @Test
    @DisplayName("復元した集約は法人契約を持つ（イベントが運んでいる値を捨てない）")
    void restoresCorporateContract() {
        Shipper shipper = new Shipper();

        shipper.on(new ShipperRegisteredEvent("SHP-000011", "CORPORATE", "山田商事",
                "sales@example.com", "03-1111-1111", "東京都中央区", "CT-0001", "0.1000"));

        assertThat(shipper.corporateContract())
                .as("捨てると、リプレイした集約だけが契約を持たず、契約変更や割引の"
                        + "不変条件を足した瞬間に誤判断する")
                .hasValueSatisfying(contract ->
                        assertThat(contract.contractNumber()).isEqualTo("CT-0001"));
        assertThat(shipper.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(shipper.shipperId()).isEqualTo("SHP-000011");
    }

    @Test
    @DisplayName("個人の荷主は復元しても法人契約を持たない")
    void restoresIndividualWithoutContract() {
        Shipper shipper = new Shipper();

        shipper.on(new ShipperRegisteredEvent("SHP-000012", "INDIVIDUAL", "山田太郎",
                "yamada@example.com", "03-0000-0000", "東京都港区", null, null));

        assertThat(shipper.corporateContract()).isEmpty();
    }

    @Test
    @DisplayName("個人情報が読める荷主は削除済みではない")
    void isNotShreddedWhenReadable() {
        Shipper shipper = new Shipper();

        shipper.on(new ShipperRegisteredEvent("SHP-000010", "CORPORATE", "山田商事",
                "sales@example.com", "03-1111-1111", "東京都中央区", "CT-0001", "0.1000"));

        assertThat(shipper.isShredded()).isFalse();
    }

    @Test
    @DisplayName("荷主 ID が空なら受け付けない")
    void rejectsBlankShipperId() {
        RegisterShipperCommand command = new RegisterShipperCommand("  ", "山田太郎",
                ShipperType.INDIVIDUAL, new Email("yamada@example.com"), "03-0000-0000",
                "東京都港区", null);

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().exception(IllegalArgumentException.class, "荷主 ID は必須です");
    }

    @Test
    @DisplayName("荷主種別が無ければ受け付けない")
    void rejectsMissingShipperType() {
        RegisterShipperCommand command = new RegisterShipperCommand("SHP-000006", "山田太郎",
                null, new Email("yamada@example.com"), "03-0000-0000", "東京都港区", null);

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().exception(IllegalArgumentException.class, "荷主種別は必須です");
    }

    @Test
    @DisplayName("荷主名が空なら受け付けない")
    void rejectsBlankName() {
        RegisterShipperCommand command = new RegisterShipperCommand("SHP-000005", "  ",
                ShipperType.INDIVIDUAL, new Email("yamada@example.com"), "03-0000-0000",
                "東京都港区", null);

        fixture.given().noPriorActivity()
                .when().command(command)
                .then().exception(IllegalArgumentException.class, "荷主名は必須です");
    }
}
