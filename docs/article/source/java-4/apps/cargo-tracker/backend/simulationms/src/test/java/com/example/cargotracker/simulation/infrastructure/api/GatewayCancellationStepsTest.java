package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 輸送中キャンセルの工程（US35 §受入基準 4）。
 *
 * <p><b>例外の工程とは別のクラスにする。</b> 1 つのファイルに積むと読みどころが
 * 埋もれ、行の上限（500 行）にも当たる——割る理由は行数だが、割り方は
 * 「何を確かめているか」で決める。</p>
 */
class GatewayCancellationStepsTest extends GatewayStepsTestSupport {
    @Test
    @DisplayName("US35 §4: 承認は候補から陸揚げ地を選び、次の工程へ渡す")
    void approvesTheCancellationAtACandidatePort() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
                "{\"currentUnLocode\":\"JPTYO\",\"unLocodes\":[\"SGSIN\"]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.APPROVE_CANCELLATION, inTransit());

        assertThat(result.producedId())
                .as("**指定した港を渡す**——渡さないと承認と荷降しが別の港を指しうる")
                .isEqualTo("SGSIN");
        assertThat(bodies).anyMatch(body ->
                body.contains("/cancellation/approval") && body.contains("SGSIN"));
    }

    @Test
    @DisplayName("US35 §4: 陸揚げ地の候補が無ければ、そう言って止まる")
    void refusesWhenThereIsNoDischargeCandidate() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
                "{\"currentUnLocode\":\"JPTYO\",\"unLocodes\":[]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.APPROVE_CANCELLATION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("陸揚げ地の候補");
    }

    @Test
    @DisplayName("US35 §4: 荷降しは承認で指定した港で記録する（当て直さない）")
    void dischargesAtTheApprovedPort() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"SGSIN\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "SGSIN");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains("UNLOAD")
                        && body.contains("SGSIN") && body.contains("V-9"));
    }

    @Test
    @DisplayName("US35 §4: 承認の港が読めなければ、当てずっぽうで降ろさない")
    void refusesToDischargeWithoutTheApprovedPort() throws IOException {
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("陸揚げ地が読めませんでした");
    }

    @Test
    @DisplayName("US35 §4: 申請は理由を添えて送る")
    void requestsTheCancellationWithAReason() throws IOException {
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.REQUEST_CANCELLATION, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/cancellation") && body.contains("reason"));
    }

    @Test
    @DisplayName("US35 §4: 荷降しを断られたら、理由を残して止まる")
    void keepsTheReasonWhenTheDischargeIsRefused() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"SGSIN\"}]}");
        statuses.put("/api/v1/handling/activities", 422);
        responses.put("/api/v1/handling/activities",
                "{\"message\":\"その港では降ろせません\"}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "SGSIN");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.failureMessage()).contains("その港では降ろせません");
    }

    @Test
    @DisplayName("US35 §4: 承認が現在地（積んだ港）を選んでも荷降しできる")
    void dischargesAtTheLoadPortWhenApproved() throws IOException {
        // **降ろす港だけを見ると見つからない。** 承認は現在地も候補に出す
        // ——積んだ港で降ろすことになったとき、区間は「積む港」として持つ。
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "JPTYO");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains("JPTYO")
                        && body.contains("V-9"));
    }

    @Test
    @DisplayName("US35 §4: 旅程に触れない港を指定されたら、そう言って止まる")
    void refusesToDischargeAtAPortNotOnTheItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "DEHAM");

        assertThat(api.execute(StepKind.DISCHARGE_CANCELLED, produced).failureMessage())
                .contains("旅程にありません");
    }

    @Test
    @DisplayName("US35 §4: 受領と積込を記録して輸送中にする（荷降しは記録しない）")
    void loadsTheCargoWithoutUnloading() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.LOAD_CARGO, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body -> body.contains("RECEIVE"))
                .anyMatch(body -> body.contains("LOAD"));
        assertThat(bodies)
                .as("**荷降しまで記録すると輸送が終わり、承認の要らないキャンセルになる**")
                .noneMatch(body -> body.contains("\"handlingType\":\"UNLOAD\""));
    }

    @Test
    @DisplayName("US35 §4: 旅程が読めなければ、当てずっぽうで積まない")
    void refusesToLoadWithoutAnItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary", "{\"legs\":[]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        assertThat(api.execute(StepKind.LOAD_CARGO, inTransit()).failureMessage())
                .contains("積込の港を決められません");
    }
}
