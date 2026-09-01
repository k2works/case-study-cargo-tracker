package com.example.simulationms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
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
 * 例外を起こし、対応する工程の出口（US36・[ADR-031] 決定 5）。
 *
 * <p><strong>専用 API を呼んでいないこと</strong>をここで固定する。呼ぶ先が
 * 荷役・通関・キャンセルという実利用者の入口であることを、要求そのもので確かめる。
 */
@DisplayName("例外の工程の呼び出し")
class RestExceptionStepsTest {

    private static final String BASE = "http://gateway.test";

    private static final String TRACKING = "TRK-20261116-0001";

    private MockRestServiceServer server;

    private RestExceptionSteps steps;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        steps = new RestExceptionSteps(builder.build(),
                Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneId.of("Asia/Tokyo")));
    }

    private static Map<String, String> context() {
        return Map.of(BusinessContextKey.RUN_ID, "SIM-20261116-0001",
                BusinessContextKey.BOOKING_ID, "1001",
                BusinessContextKey.TRACKING_NUMBER, TRACKING,
                BusinessContextKey.VOYAGE_NUMBER, "V-SIM-20261116-0001",
                BusinessContextKey.DECLARATION_ID, "77");
    }

    /**
     * <strong>誤配は荷役の記録から起こす</strong>（[ADR-026]・[ADR-031] 決定 5）。
     *
     * <p>誤配の起票はここでは行わない。記録そのものから検知されるのが決定であり、
     * ここで起票すると<strong>検知が働いていなくても緑になる</strong>。
     */
    @Test
    @DisplayName("誤配は、予定と違う港での荷降しとして記録する")
    void misrouteIsRecordedAsHandlingAtTheWrongPort() {
        server.expect(requestTo(BASE + RestBusinessGateway.HANDLING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("RECEIVE"))
                .andRespond(withSuccess());
        server.expect(requestTo(BASE + RestBusinessGateway.HANDLING_PATH))
                .andExpect(jsonPath("$.type").value("LOAD"))
                .andRespond(withSuccess());
        server.expect(requestTo(BASE + RestBusinessGateway.HANDLING_PATH))
                .andExpect(jsonPath("$.type").value("UNLOAD"))
                // **予定と違う港**。ここが予定どおりだと、誤配は起きない
                .andExpect(jsonPath("$.locationUnLocode").value("SGSIN"))
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.RECORD_MISROUTED_HANDLING, "token", context());

        server.verify();
    }

    /** 保留と解除は、通関の状態を変える実利用者の操作である（UC21）。 */
    @Test
    @DisplayName("税関保留は、通関の状態を HELD に変える")
    void customsHoldChangesTheDeclarationStatus() {
        server.expect(requestTo(BASE + RestBusinessGateway.CUSTOMS_PATH + "/77/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.HOLD_CUSTOMS, "token", context());

        server.verify();
    }

    /** キャンセルの承認には陸揚げ地が要る（[ADR-025] 決定 4）。 */
    @Test
    @DisplayName("キャンセル承認は、陸揚げ地を添えて送る")
    void approvalCarriesTheDischargeLocation() {
        server.expect(requestTo(BASE + "/api/v1/bookings/1001/cancellation/approve"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.dischargeLocationUnLocode").value("USLAX"))
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.APPROVE_CANCELLATION, "token", context());

        server.verify();
    }

    /**
     * <strong>解決する相手を読んでから解決する。</strong>番号を推測すると、
     * 別の貨物の例外を閉じうる——閉じた側は誰にも気づかれない。
     */
    @Test
    @DisplayName("例外の解決は、起きている例外の番号を読んでから送る")
    void resolveReadsTheRaisedIssueFirst() {
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH + "/" + TRACKING))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"trackingNumber": "%s",
                         "activeException": {"id": 5, "exceptionType": "DELAY"}}
                        """.formatted(TRACKING), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH
                        + "/exceptions/5/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.exceptionId").value(5))
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.RESOLVE_EXCEPTION, "token", context());

        server.verify();
    }

    /**
     * <strong>遅延の解決には新しい到着予定日を添える</strong>（US19-4）。
     *
     * <p>添えないと集約が断る——断られること自体は正しい振る舞いであり、こちらの
     * 入力が足りていない。実環境で実際に踏んだ（400: 遅延を解決するときは、
     * 新しい到着予定日を入れてください）。
     */
    @Test
    @DisplayName("遅延の解決には、新しい到着予定日を添える")
    void resolvingADelayCarriesTheNewArrival() {
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH + "/" + TRACKING))
                .andRespond(withSuccess("""
                        {"trackingNumber": "%s",
                         "activeException": {"id": 5, "exceptionType": "DELAY"}}
                        """.formatted(TRACKING), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH
                        + "/exceptions/5/resolve"))
                .andExpect(jsonPath("$.newEstimatedArrival").exists())
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.RESOLVE_EXCEPTION, "token", context());

        server.verify();
    }

    /** 遅延以外では据え置く（空なら据え置く仕様）。 */
    @Test
    @DisplayName("遅延以外の解決では、到着予定日を送らない")
    void resolvingOtherExceptionsLeavesTheArrivalAlone() {
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH + "/" + TRACKING))
                .andRespond(withSuccess("""
                        {"trackingNumber": "%s",
                         "activeException": {"id": 6, "exceptionType": "DAMAGE"}}
                        """.formatted(TRACKING), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH
                        + "/exceptions/6/resolve"))
                .andExpect(jsonPath("$.newEstimatedArrival").doesNotExist())
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.RESOLVE_EXCEPTION, "token", context());

        server.verify();
    }

    /**
     * <strong>例外が起きていないことを、成功にしない。</strong>起こす工程が実際には
     * 起こしていなかった場合、ここで黙って通すと「例外シナリオが通った」と読める。
     */
    @Test
    @DisplayName("解決すべき例外が無ければ、理由を言って止まる")
    void failsWhenThereIsNothingToResolve() {
        server.expect(requestTo(BASE + RestBusinessGateway.TRACKING_MANAGE_PATH + "/" + TRACKING))
                .andRespond(withSuccess("""
                        {"trackingNumber": "%s", "activeException": null}
                        """.formatted(TRACKING), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
                        steps.execute(ScenarioStep.RESOLVE_EXCEPTION, "token", context()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("解決すべき例外が起きていません");
    }

    /**
     * 誤配のあとは<strong>現在地からの航海を登録する</strong>（US36-3）。
     *
     * <p>元の航海は誤配した港からの区間を持たないため、そのまま割り当てようとすると
     * 409（選んだ経路はもう使えません）で断られる——<strong>実環境で実際に踏んだ</strong>。
     * 経路設計者が現在地からの航海を探し、無ければ登録するのが実業務の手順である。
     */
    @Test
    @DisplayName("組み直し用の航海は、誤配した港から目的地へ向かう")
    void registersARecoveryVoyageFromTheWrongPort() {
        server.expect(requestTo(BASE + RestBusinessGateway.VOYAGE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.movements[0].departureUnLocode").value("SGSIN"))
                .andExpect(jsonPath("$.movements[0].arrivalUnLocode").value("USLAX"))
                .andRespond(withSuccess());

        String number = steps.execute(
                ScenarioStep.REGISTER_RECOVERY_VOYAGE, "token", context());

        assertThat(number).isEqualTo("VR-20261116-0001");
        server.verify();
    }

    /**
     * 経路の組み直しは<strong>現在地を出発地にする</strong>（US36-3）。
     *
     * <p><strong>レグは自分で組み立てない。</strong>組み立てると航海のスケジュールと
     * 食い違い、409 で断られる（実環境で実際に踏んだ）。候補を引き、
     * 組み直し用の航海を通る候補を選ぶ——実際の経路設計者と同じ手順である。
     */
    @Test
    @DisplayName("経路の組み直しは、候補を引いて組み直し用の航海を選ぶ")
    void redesignPicksTheCandidateOnTheRecoveryVoyage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE + RestBusinessGateway.ROUTE_PATH)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"candidates": [
                          {"legs": [{"voyageNumber": "V-OTHER-0001",
                                     "fromUnLocode": "SGSIN", "toUnLocode": "USLAX",
                                     "departureTime": "2026-11-17T00:00:00Z",
                                     "arrivalTime": "2026-11-27T00:00:00Z"}]},
                          {"legs": [{"voyageNumber": "VR-20261116-0001",
                                     "fromUnLocode": "SGSIN", "toUnLocode": "USLAX",
                                     "departureTime": "2026-11-17T00:00:00Z",
                                     "arrivalTime": "2026-11-27T00:00:00Z"}]}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/bookings/1001/route"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.legs[0].voyageNumber")
                        .value("VR-20261116-0001"))
                .andExpect(jsonPath("$.legs[0].loadUnLocode").value("SGSIN"))
                .andExpect(jsonPath("$.legs[0].unloadUnLocode").value("USLAX"))
                .andRespond(withSuccess());

        steps.execute(ScenarioStep.REDESIGN_ROUTE, "token", recoveryContext());

        server.verify();
    }

    /**
     * <strong>候補が無いことを、成功にしない。</strong>組み直しの航海が登録できて
     * いないのに割り当てが通ると、輸送が再開したように見えて再開していない。
     */
    @Test
    @DisplayName("組み直し用の航海を通る候補が無ければ、理由を言って止まる")
    void failsWhenNoCandidateUsesTheRecoveryVoyage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE + RestBusinessGateway.ROUTE_PATH)))
                .andRespond(withSuccess("""
                        {"candidates": [
                          {"legs": [{"voyageNumber": "V-OTHER-0001",
                                     "fromUnLocode": "SGSIN", "toUnLocode": "USLAX",
                                     "departureTime": "2026-11-17T00:00:00Z",
                                     "arrivalTime": "2026-11-27T00:00:00Z"}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
                        steps.execute(ScenarioStep.REDESIGN_ROUTE, "token", recoveryContext()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("VR-20261116-0001");
    }

    /** 組み直しの工程は、組み直し用の航海を引き継いでいる。 */
    private static Map<String, String> recoveryContext() {
        Map<String, String> context = new java.util.HashMap<>(context());
        context.put(BusinessContextKey.RECOVERY_VOYAGE_NUMBER, "VR-20261116-0001");
        return Map.copyOf(context);
    }

    /**
     * <strong>航海番号は 20 文字に収まる。</strong>{@code voyage_number} は
     * {@code VARCHAR(20)} である——長い名前を送ると「value too long」で 500 になり、
     * <strong>実環境でしか出ない</strong>。実際に踏んだので検査で固定する。
     */
    @Test
    @DisplayName("シミュレーションが作る航海番号は、列の長さに収まる")
    void voyageNumbersFitTheColumn() {
        String runId = "SIM-20261116-0001";

        assertThat(RestBusinessGateway.voyageNumberOf(runId)).hasSizeLessThanOrEqualTo(20);
        assertThat(RestExceptionSteps.recoveryVoyageNumberOf(runId))
                .hasSizeLessThanOrEqualTo(20);
    }

    /** 例外の工程でないものを渡されたら、黙って通さない。 */
    @Test
    @DisplayName("例外の工程でなければ、名前を挙げて断る")
    void rejectsAStepThatIsNotAnException() {
        assertThatThrownBy(() ->
                        steps.execute(ScenarioStep.SETTLE, "token", context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SETTLE");
    }
}
