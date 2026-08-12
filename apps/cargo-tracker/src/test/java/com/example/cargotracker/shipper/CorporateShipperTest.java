package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.entities.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 法人荷主（US03）。
 *
 * <p><strong>{@code CorporateShipper} として {@code Shipper} を継承しない</strong>
 * （IT7 設計反映 #12）。{@code domain-model.md} はサブタイプとして定義していたが、
 * 実装の {@code Shipper} は {@code final} かつ不変であり、継承すると
 * <strong>「法人なのに契約が無い」「個人なのに契約がある」組み合わせを型で防げなくなる</strong>。
 *
 * <p>契約番号と割引率を<strong>ひと組の値オブジェクト</strong>として荷主が持つ形に改めた。
 * IT6 の {@code ProposedRoute.Path}・{@code CargoProgress}、本 IT の
 * {@code HandledCargo}・{@code HandlingDetails} と同じ判断である。
 */
@DisplayName("法人荷主（US03）")
class CorporateShipperTest {

    private static final ShipperCode コード = new ShipperCode("SHP-000001");
    private static final ShipperName 名称 = new ShipperName("山田物産株式会社");

    private static Shipper 法人(CorporateContract contract) {
        return Shipper.registerCorporate(
                new ShipperId(UUID.randomUUID()), コード, 名称,
                new Email("corp@example.com"), new Phone("06-1234-5678"),
                new Address("JP", "530-0001", "大阪府", "大阪市北区", "梅田 1-1-1"),
                contract);
    }

    private static CorporateContract 契約(String rate) {
        return new CorporateContract(
                new ContractNumber("CT-2026-0001"), new DiscountRate(new BigDecimal(rate)));
    }

    // ---- 種別と契約はひと組 ----

    /** 受入基準: 法人荷主で登録完了後、荷主 ID が発行される。 */
    @Test
    void 法人荷主は契約番号と割引率を持つ() {
        var shipper = 法人(契約("0.1000"));

        assertThat(shipper.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(shipper.contract().contractNumber().value()).isEqualTo("CT-2026-0001");
        assertThat(shipper.contract().discountRate().value())
                .isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    /**
     * <strong>契約の無い法人は作れない。</strong> 契約番号が無いと、
     * 精算のときに<strong>どの契約に基づく割引かを説明できない</strong>。
     * DB の {@code chk_shipper_corporate_contract} と同じ不変条件である。
     */
    @Test
    void 契約の無い法人荷主は登録できない() {
        assertThatThrownBy(() -> 法人(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("契約");
    }

    /**
     * <strong>個人荷主は契約を持たない。</strong> 個人には契約割引の概念そのものが無い
     * （{@code ui_design.md}: 個人は割引率欄に {@code -} を表示する。
     * <strong>0% と {@code -} は意味が違う</strong>）。
     */
    @Test
    void 個人荷主は契約を持たない() {
        var shipper = Shipper.registerIndividual(
                new ShipperId(UUID.randomUUID()), コード, 名称,
                new Email("kojin@example.com"), new Phone("06-1234-5678"),
                new Address("JP", "530-0001", "大阪府", "大阪市北区", "梅田 1-1-1"));

        assertThat(shipper.shipperType()).isEqualTo(ShipperType.INDIVIDUAL);
        assertThat(shipper.contract()).isNull();
        assertThat(shipper.hasContract()).isFalse();
    }

    // ---- 割引率の値域 ----

    /**
     * <strong>上限 30% はドメインの不変条件である</strong>
     * （{@code domain-model.md} ビジネスルール 4）。
     * <strong>画面に別の上限を書かない。</strong> 旧版の割引ポリシー画面は
     * -50〜100% を許容しており、画面から入力できてドメインが弾く状態だった。
     */
    @ParameterizedTest
    @ValueSource(strings = {"0.0000", "0.1500", "0.3000"})
    void 割引率は0から30パーセントまで許す(String rate) {
        assertThatCode(() -> new DiscountRate(new BigDecimal(rate)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.3001", "0.5000", "1.0000"})
    void 上限を超える割引率は作れない(String rate) {
        assertThatThrownBy(() -> new DiscountRate(new BigDecimal(rate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
    }

    /** <strong>負の割引率は「割増」である。</strong> それは割引ではない。 */
    @ParameterizedTest
    @ValueSource(strings = {"-0.0001", "-0.1000"})
    void 負の割引率は作れない(String rate) {
        assertThatThrownBy(() -> new DiscountRate(new BigDecimal(rate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 契約番号は空にできない（どの契約か特定できない記録を残さない）。 */
    @Test
    void 契約番号は空にできない() {
        assertThatThrownBy(() -> new ContractNumber(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("契約番号");
    }

    // ---- 訂正 ----

    /**
     * 契約条件を訂正できる（US32 と同じ「訂正」のことば）。
     *
     * <p><strong>Setter を生やさない。</strong> どの操作が業務操作ログに残るべきかが
     * コードから読めるようにする。
     */
    @Test
    void 契約条件を訂正できる() {
        var shipper = 法人(契約("0.1000"));

        var corrected = shipper.changeContract(契約("0.2000"));

        assertThat(corrected.contract().discountRate().value())
                .isEqualByComparingTo(new BigDecimal("0.2000"));
        // **元のインスタンスは変わらない**（不変であること）
        assertThat(shipper.contract().discountRate().value())
                .isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    /** <strong>個人荷主に契約は付けられない。</strong> 種別と契約は常にひと組である。 */
    @Test
    void 個人荷主に契約は付けられない() {
        var shipper = Shipper.registerIndividual(
                new ShipperId(UUID.randomUUID()), コード, 名称,
                new Email("kojin@example.com"), new Phone("06-1234-5678"),
                new Address("JP", "530-0001", "大阪府", "大阪市北区", "梅田 1-1-1"));

        // 戻り値を握りつぶさない（新しい荷主を返すメソッドであり、
        // 個人荷主では**返る前に**弾かれることを確かめている）
        assertThatThrownBy(() -> assertThat(shipper.changeContract(契約("0.1000"))).isNotNull())
                .isInstanceOf(IllegalStateException.class);
    }
}
