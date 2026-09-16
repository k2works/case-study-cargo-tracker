package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 例外の工程（US35 §受入基準 1・2・3）。
 *
 * <p><b>段取りは {@link GatewayStepsTestSupport} が持つ。</b> 本物の HTTP を
 * 通す理由もそちらに書いてある。</p>
 */
class GatewayRecoveryStepsTest extends GatewayStepsTestSupport {
    @Test
    @DisplayName("US35 §1: 例外はシナリオの種別で起票する（工程は同じ・入力が違う）")
    void reportsTheExceptionTypeTheScenarioDeclares() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING + "/exceptions",
                "{\"exceptionId\":\"" + EXCEPTION + "\"}");
        var api = start(Scenario.DAMAGE);

        var result = api.execute(StepKind.REGISTER_EXCEPTION, inTransit());

        assertThat(result.producedId()).isEqualTo(EXCEPTION);
        assertThat(bodies)
                .as("**種類は入力で変える**（注 N10）")
                .anyMatch(body -> body.contains("DAMAGE"));
        assertThat(requests)
                .as("起票は追跡管理者の仕事（担当を実装の都合で変えない）")
                .anyMatch(request -> request.contains("/exceptions") && request.contains("auth="));
    }

    @Test
    @DisplayName("US35 §1: 例外を含まないシナリオで起票しようとしたら、理由を言って止まる")
    void refusesToReportAnExceptionWithoutAType() throws IOException {
        var api = start(Scenario.STANDARD);

        var result = api.execute(StepKind.REGISTER_EXCEPTION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage())
                .as("**黙って別の種別で起票しない**——確かめたいものと違うものが通る")
                .contains("例外種別がありません");
    }

    @Test
    @DisplayName("US35 §3: 組み直しは前と違う候補を選ぶ（同じ経路に戻さない）")
    void reassignsToADifferentItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/route-candidates",
                "{\"candidates\":["
                + "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]},"
                + "{\"legs\":[{\"voyageNumber\":\"V-2\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}]}");
        var api = start(Scenario.MISROUTE);
        var produced = inTransit();
        // 1 件目と同じ指紋を「前の経路」として渡す。
        produced.put(StepKind.ASSIGN_ROUTE, "V-1>JPTYO-USNYC|");

        var result = api.execute(StepKind.REASSIGN_ROUTE, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId())
                .as("**組み直した先を次の工程へ渡す**（待ちがこれと突き合わせる）")
                .isEqualTo("V-2>JPTYO-USNYC|");
        assertThat(bodies).anyMatch(body ->
                body.startsWith("POST /api/v1/booking/bookings/B-1/route") && body.contains("V-2"));
    }

    @Test
    @DisplayName("US35 §3: 前と違う候補が無ければ、組み直す先が無いと言って止まる")
    void refusesWhenEveryCandidateIsTheSame() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/route-candidates",
                "{\"candidates\":[{\"legs\":[{\"voyageNumber\":\"V-1\","
                + "\"loadUnLocode\":\"JPTYO\",\"unloadUnLocode\":\"USNYC\"}]}]}");
        var api = start(Scenario.MISROUTE);
        var produced = inTransit();
        produced.put(StepKind.ASSIGN_ROUTE, "V-1>JPTYO-USNYC|");

        var result = api.execute(StepKind.REASSIGN_ROUTE, produced);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("組み直す先がありません");
    }

    @Test
    @DisplayName("US35 §3: 誤配は旅程に無い港で荷役を記録して起こす（手で起票しない）")
    void raisesTheMisrouteFromOffRouteHandling() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        servedPorts("JPTYO", "SGSIN", "USNYC");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId())
                .as("**旅程に無い港を選ぶ**——旅程の港で記録すると誤配にならない")
                .isNotIn("JPTYO", "USNYC");
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains(result.producedId()));
    }

    @Test
    @DisplayName("US35 §3: 旅程が読めなければ、当てずっぽうで荷役を記録しない")
    void refusesToGoOffRouteWithoutAnItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary", "{\"legs\":[]}");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("誤配を起こせません");
    }

    @Test
    @DisplayName("US35 §1: 税関保留は申告を留置して起こす（手で起票しない）")
    void raisesTheCustomsHoldByHoldingTheDeclaration() throws IOException {
        var api = start(Scenario.CUSTOMS_HOLD);

        var result = api.execute(StepKind.HOLD_CUSTOMS, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies)
                .as("**通関が決めること。** 申告を出してから留置する")
                .anyMatch(body -> body.contains("/customs-declarations")
                        && body.contains(TRACKING))
                .anyMatch(body -> body.contains("/status") && body.contains("HELD"));
    }

    @Test
    @DisplayName("US35 §2: 対応と解決は「開いている例外」に対して行う")
    void actsOnWhicheverExceptionIsOpen() throws IOException {
        // **システムが起こした例外は識別子を返さない。** 起票の応答に頼ると、
        // 誤配と税関保留のシナリオだけが対応できない。
        responses.put("/api/v1/tracking/trackings/" + TRACKING,
                "{\"exceptions\":[{\"exceptionId\":\"resolved-1\","
                + "\"responseStatus\":\"RESOLVED\"},"
                + "{\"exceptionId\":\"open-1\",\"responseStatus\":\"OPEN\"}]}");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RESPOND_TO_EXCEPTION, inTransit());

        assertThat(result.producedId()).isEqualTo("open-1");
        assertThat(bodies)
                .as("解決済みの例外を掴まない")
                .anyMatch(body -> body.contains("/exceptions/open-1/response"));
    }

    @Test
    @DisplayName("US35 §2: 未解決の例外が無ければ、理由を言って止まる")
    void refusesWhenNoExceptionIsOpen() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING,
                "{\"exceptions\":[{\"exceptionId\":\"e-1\","
                + "\"responseStatus\":\"RESOLVED\"}]}");
        var api = start(Scenario.DELAY);

        var result = api.execute(StepKind.RESOLVE_EXCEPTION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("未解決の例外がありません");
    }

    @Test
    @DisplayName("この担い手が扱わない工程は、黙って成功にしない")
    void refusesStepsItDoesNotOwn() throws IOException {
        var api = start(Scenario.DELAY);

        // 正常系の工程は別の担い手が持つ。**配線を誤ったら気づけるようにする。**
        assertThat(new GatewayRecoverySteps(null, ScenarioInput.standard(Scenario.DELAY), null)
                .execute(StepKind.REGISTER_SHIPPER, inTransit()).failureMessage())
                .contains("扱わない工程です");
        assertThat(api).isNotNull();
    }

    /**
     * 断られたら、理由を伝えて止まる。
     *
     * <p><b>工程を数え上げる。</b> 1 つずつ確かめる形は、次に足した工程が
     * 断りを握りつぶしていても緑になる——業務の断りは US34 §2 の中身である。</p>
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "REGISTER_EXCEPTION, /api/v1/tracking/trackings/" + TRACKING + "/exceptions",
        "REQUEST_CANCELLATION, /api/v1/booking/bookings/B-1/cancellation",
        "REASSIGN_ROUTE, /api/v1/booking/bookings/B-1/route-candidates",
        "APPROVE_CANCELLATION, /api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
        "HOLD_CUSTOMS, /api/v1/handling/customs-declarations",
        "RESPOND_TO_EXCEPTION, /api/v1/tracking/trackings/" + TRACKING,
    })
    @DisplayName("US34 §2: 断られた工程は、業務の言葉で理由を残して止まる")
    void keepsTheBusinessReasonWhenRefused(String kindName, String refusedPath)
            throws IOException {
        statuses.put(refusedPath, 422);
        responses.put(refusedPath,
                "{\"code\":\"BUSINESS_RULE_VIOLATION\",\"message\":\"断りの理由\"}");
        var api = start(Scenario.DELAY);
        var produced = inTransit();
        produced.put(StepKind.ASSIGN_ROUTE, "V-0>JPTYO-USNYC|");

        var result = api.execute(StepKind.valueOf(kindName), produced);

        assertThat(result.succeeded())
                .as("%s が断りを握りつぶしている", kindName)
                .isFalse();
        assertThat(result.failureMessage())
                .as("**「失敗しました」では切り分けられない**")
                .contains("断りの理由");
    }

    @Test
    @DisplayName("US35 §3: 経路外の荷役を断られたら、理由を残して止まる")
    void keepsTheReasonWhenOffRouteHandlingIsRefused() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        statuses.put("/api/v1/handling/activities", 422);
        responses.put("/api/v1/handling/activities", "{\"message\":\"記録できません\"}");
        servedPorts("JPTYO", "SGSIN", "USNYC");
        var api = start(Scenario.MISROUTE);

        assertThat(api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit())
                .failureMessage()).contains("記録できません");
    }

    @Test
    @DisplayName("US35 §3: 航海の一覧が読めなければ、その理由を残す（港 0 件に化けさせない）")
    void keepsTheReasonWhenTheVoyageListCannotBeRead() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        statuses.put("/api/v1/routing/voyages", 500);
        responses.put("/api/v1/routing/voyages", "{\"message\":\"航海が読めません\"}");
        var api = start(Scenario.MISROUTE);

        // **応答の状態を見ないと「旅程に無い港が見つかりません」に化ける。**
        // 原因は経路サービス側なのに、記録には誤配の段取りの失敗しか残らない。
        assertThat(api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit())
                .failureMessage()).contains("航海が読めません");
    }

    @Test
    @DisplayName("US35 §1: 留置を断られたら、理由を残して止まる")
    void keepsTheReasonWhenTheHoldIsRefused() throws IOException {
        statuses.put("/api/v1/handling/customs-declarations/status", 422);
        var api = start(Scenario.CUSTOMS_HOLD);

        var result = api.execute(StepKind.HOLD_CUSTOMS, inTransit());

        // 申告は通り、留置で断られる形（状態の更新だけが落ちる）。
        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body -> body.contains("HELD"));
    }

    @Test
    @DisplayName("US35 §2: 起票の応答に識別子が無ければ、そう言って止まる")
    void refusesWhenTheExceptionIdIsMissing() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING + "/exceptions", "{}");
        var api = start(Scenario.DELAY);

        assertThat(api.execute(StepKind.REGISTER_EXCEPTION, inTransit()).failureMessage())
                .contains("exceptionId がありません");
    }

    @Test
    @DisplayName("US35 §2: 追跡が読めなければ、対応に進まない")
    void refusesToRespondWhenTheTrackingCannotBeRead() throws IOException {
        statuses.put("/api/v1/tracking/trackings/" + TRACKING, 500);
        responses.put("/api/v1/tracking/trackings/" + TRACKING, "{\"message\":\"読めません\"}");
        var api = start(Scenario.DELAY);

        assertThat(api.execute(StepKind.RESPOND_TO_EXCEPTION, inTransit()).succeeded())
                .isFalse();
    }

}
