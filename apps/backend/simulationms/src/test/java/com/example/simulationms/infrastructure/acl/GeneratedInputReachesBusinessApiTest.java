package com.example.simulationms.infrastructure.acl;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 乱数が選んだ入力が、業務 API まで届くこと（US37-1）。
 *
 * <p><strong>生成器が選んでも、途中の層で捨てられれば意味が無い。</strong>
 * IT15 の実装では、生成器が出発地・目的地・貨物種別・重量・期限を選んでいたのに
 * 予約登録は固定値を送っていた——生成器だけを見るテストは緑のままだった。
 * 値は全層を生き延びるか確かめる。
 *
 * <p>{@link RestBusinessGatewayTest} から分けたのは行数の都合ではなく、
 * <strong>変わる理由が違う</strong>ためである。あちらは「本番の経路を踏むこと」を
 * 見ており、ここは「乱数の入力が届くこと」を見る。
 */
@DisplayName("乱数が選んだ入力の行き先")
class GeneratedInputReachesBusinessApiTest {

    private static final String BASE = "http://gateway.test";

    private MockRestServiceServer server;

    private RestBusinessGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RestBusinessGateway(builder.build(), SimulationUsers.of(
                Map.of("ROLE_SALES", "sim-sales01", "ROLE_ROUTING", "sim-routing01",
                        "ROLE_HANDLER", "sim-handler01", "ROLE_TRACKER", "sim-tracker01",
                        "ROLE_ACCOUNTANT", "sim-accountant01"), "password"),
                Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneId.of("Asia/Tokyo")));
    }

    private void expectLoginAs(String username, String token) {
        server.expect(requestTo(BASE + RestBusinessGateway.LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.userId").value(username))
                .andRespond(withSuccess(
                        "{\"token\":\"" + token + "\",\"userId\":\"" + username + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    /**
     * <strong>貨物種別によって要る項目が変わる</strong>（US04 の不変条件）。
     *
     * <p>冷凍・冷蔵は保管温度、危険物は危険物申告が要る。添えないと集約が断る
     * ——実環境で 23 件落ちた。断られること自体は正しい振る舞いであり、
     * こちらの入力が足りていない。乱数が種別を選ぶようになって初めて表面化した。
     */
    @Test
    @DisplayName("冷蔵貨物の予約には、保管温度を添える")
    void refrigeratedBookingCarriesTheTemperature() {
        expectLoginAs("sim-sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("REFRIGERATED"))
                .andExpect(jsonPath("$.minCelsius").exists())
                .andExpect(jsonPath("$.maxCelsius").exists())
                .andRespond(withSuccess("{\"bookingId\":\"BKG-2026000001\"}",
                        MediaType.APPLICATION_JSON));

        gateway.execute(ScenarioStep.REGISTER_BOOKING, Map.of(
                BusinessContextKey.SHIPPER_ID, "1",
                BusinessContextKey.CARGO_TYPE, "REFRIGERATED"));

        server.verify();
    }

    @Test
    @DisplayName("危険物の予約には、危険物申告を添える")
    void hazardousBookingCarriesTheDeclaration() {
        expectLoginAs("sim-sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("HAZARDOUS"))
                .andExpect(jsonPath("$.hazardousClass").exists())
                .andExpect(jsonPath("$.unNumber").exists())
                .andRespond(withSuccess("{\"bookingId\":\"BKG-2026000001\"}",
                        MediaType.APPLICATION_JSON));

        gateway.execute(ScenarioStep.REGISTER_BOOKING, Map.of(
                BusinessContextKey.SHIPPER_ID, "1",
                BusinessContextKey.CARGO_TYPE, "HAZARDOUS"));

        server.verify();
    }

    /** 一般貨物には**添えない**。要らない項目を送ると、集約が別の理由で断りうる。 */
    @Test
    @DisplayName("一般貨物の予約には、温度も危険物申告も添えない")
    void generalBookingCarriesNeither() {
        expectLoginAs("sim-sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andExpect(jsonPath("$.type").value("GENERAL"))
                .andExpect(jsonPath("$.minCelsius").doesNotExist())
                .andExpect(jsonPath("$.hazardousClass").doesNotExist())
                .andRespond(withSuccess("{\"bookingId\":\"BKG-2026000001\"}",
                        MediaType.APPLICATION_JSON));

        gateway.execute(ScenarioStep.REGISTER_BOOKING, Map.of(
                BusinessContextKey.SHIPPER_ID, "1"));

        server.verify();
    }

    /** <strong>乱数が選んだ入力が、そのまま業務 API へ届く</strong>（US37-1）。 */
    @Test
    @DisplayName("乱数が選んだ出発地・目的地・重量が、予約に届く")
    void generatedInputReachesTheBooking() {
        expectLoginAs("sim-sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andExpect(jsonPath("$.originUnLocode").value("DEHAM"))
                .andExpect(jsonPath("$.destinationUnLocode").value("CNSHA"))
                .andExpect(jsonPath("$.weightKg").value(12345))
                .andRespond(withSuccess("{\"bookingId\":\"BKG-2026000001\"}",
                        MediaType.APPLICATION_JSON));

        gateway.execute(ScenarioStep.REGISTER_BOOKING, Map.of(
                BusinessContextKey.SHIPPER_ID, "1",
                BusinessContextKey.ORIGIN, "DEHAM",
                BusinessContextKey.DESTINATION, "CNSHA",
                BusinessContextKey.WEIGHT_KG, "12345"));

        server.verify();
    }

    /**
     * <strong>航海が受け入れる貨物種別を、運ぶものに合わせる</strong>（US37-1）。
     *
     * <p>固定にすると、乱数が冷蔵や危険物を選んだ実行で経路候補が 0 件になる
     * ——実環境で 18 件踏んだ。生成器が種別を選ぶようになって初めて表面化した。
     */
    @Test
    @DisplayName("航海は、この実行が運ぶ貨物種別を受け入れる")
    void voyageAcceptsTheCargoTypeOfThisRun() {
        expectLoginAs("sim-routing01", "token-routing");
        server.expect(requestTo(BASE + RestBusinessGateway.VOYAGE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.supportedCargoTypes[0]").value("REFRIGERATED"))
                .andRespond(withSuccess());

        gateway.execute(ScenarioStep.REGISTER_VOYAGE, Map.of(
                BusinessContextKey.RUN_ID, "SIM-20261116-0001",
                BusinessContextKey.CARGO_TYPE, "REFRIGERATED"));

        server.verify();
    }
}
