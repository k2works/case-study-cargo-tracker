package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StubShipperInfoAcl} のユニットテスト（US22 / IT7 タスク 3.2）。
 *
 * <p>IT7 暫定実装の振る舞いを検証。IT8 で {@code RestShipperInfoAcl} に置き換える際は
 * 本テストはそのまま残し、Rest 実装側に同等の WireMock テストを追加する。</p>
 */
class StubShipperInfoAclTest {

    private final ShipperInfoAcl acl = new StubShipperInfoAcl();

    @Test
    @DisplayName("US22: S-001（一般 shipperId）は CORPORATE 15% を返す（S23 UI サンプル）")
    void 一般shipperIdはCORPORATE() {
        CorporateContract contract = acl.getContract("S-001");

        assertThat(contract.shipperId()).isEqualTo("S-001");
        assertThat(contract.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(contract.discountRate()).isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("US22: S-INDIVIDUAL-* prefix は INDIVIDUAL（割引率 0）を返す")
    void INDIVIDUAL_prefix() {
        CorporateContract contract = acl.getContract("S-INDIVIDUAL-001");

        assertThat(contract.shipperType()).isEqualTo(ShipperType.INDIVIDUAL);
        assertThat(contract.discountRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("US22: 異なる shipperId でも CORPORATE で同じ 15% を返す（Stub の決定性）")
    void Stub決定性() {
        CorporateContract c1 = acl.getContract("S-YAMADA");
        CorporateContract c2 = acl.getContract("S-TANAKA");

        assertThat(c1.discountRate()).isEqualByComparingTo("0.15");
        assertThat(c2.discountRate()).isEqualByComparingTo("0.15");
    }
}
