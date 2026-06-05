package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RestShipperInfoAcl} 単体テスト（IT8 T4.1 / ADR-0015 後半）。
 *
 * <p>WireMock で bookingms {@code /api/v1/shippers/{id}} のスタブを立て、
 * RestClient → JSON parse → {@link CorporateContract} 構築の経路を検証する。
 * {@code @Cacheable} と {@code @CircuitBreaker} は Spring Context が必要なため、
 * 本単体テストでは {@link RestShipperInfoAcl#getContract(String)} を直接呼び出して
 * REST 呼出 + JSON 解析ロジックのみを検証する（Resilience4j 経路は T4.3 統合テストで検証）。</p>
 */
class RestShipperInfoAclTest {

    private WireMockServer wireMock;
    private RestShipperInfoAcl acl;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();

        registry = new SimpleMeterRegistry();
        acl = new RestShipperInfoAcl(client, registry);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("ADR-0015: CORPORATE 荷主は shipperType + discountRate を CorporateContract にマッピング")
    void corporateShipperIsMapped() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/shippers/S-001"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipperId": "S-001",
                                  "shipperType": "CORPORATE",
                                  "name": "山田商事",
                                  "contractNumber": "CT-001",
                                  "discountRate": 0.15,
                                  "active": true
                                }
                                """)));

        CorporateContract contract = acl.getContract("S-001");

        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/shippers/S-001")));
        assertThat(contract.shipperId()).isEqualTo("S-001");
        assertThat(contract.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(contract.discountRate()).isEqualByComparingTo("0.15");
        assertThat(registry.find("shipper.info.lookup").tag("result", "success").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("ADR-0015: INDIVIDUAL 荷主は discountRate=null でも 0 が強制される")
    void individualShipperForcesZeroDiscount() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/shippers/S-IND-001"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipperId": "S-IND-001",
                                  "shipperType": "INDIVIDUAL",
                                  "name": "個人 太郎",
                                  "discountRate": null,
                                  "active": true
                                }
                                """)));

        CorporateContract contract = acl.getContract("S-IND-001");

        assertThat(contract.shipperType()).isEqualTo(ShipperType.INDIVIDUAL);
        assertThat(contract.discountRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ADR-0015: 未知の shipperType は CORPORATE 扱いで縮退（discountRate=0）")
    void unknownShipperTypeFallsBackToCorporate() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/shippers/S-XX"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipperId": "S-XX",
                                  "shipperType": "GOVERNMENT",
                                  "discountRate": 0.10
                                }
                                """)));

        CorporateContract contract = acl.getContract("S-XX");

        assertThat(contract.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(contract.discountRate()).isEqualByComparingTo("0.10");
    }
}
