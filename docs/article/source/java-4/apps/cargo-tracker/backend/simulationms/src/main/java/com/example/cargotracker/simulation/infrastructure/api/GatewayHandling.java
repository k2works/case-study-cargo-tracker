package com.example.cargotracker.simulation.infrastructure.api;

import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.parse;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * 工程どうしが共有する材料（[ADR-0020] 決定 2）。
 *
 * <p><b>書き写さない。</b> 荷役の記録と旅程の読み方は正常系（{@link
 * GatewayBusinessApi}）と例外・キャンセル（{@link GatewayRecoverySteps}）の
 * 両方が要る。写すと、片方だけ直したときに食い違う。</p>
 */
final class GatewayHandling {

    private GatewayHandling() {
    }

    /** その予約の URI。 */
    static String bookingUri(Map<StepKind, String> produced, String suffix) {
        return "/api/v1/booking/bookings/" + produced.get(StepKind.REGISTER_BOOKING) + suffix;
    }

    /** 確定した旅程の区間。<b>荷役も引取も組み直しもここから港を決める</b>。 */
    static JsonNode legsOf(GatewayCalls calls, Map<StepKind, String> produced) {
        return parse(calls.get(StepRole.ROUTING, bookingUri(produced, "/itinerary")))
                .path("legs");
    }

    /** 荷役を 1 件記録する。 */
    static GatewayCalls.Response register(GatewayCalls calls, Clock clock, StepKind kind,
            String trackingNumber, String type, String unLocode, String voyageNumber,
            String consigneeName) {
        var body = new java.util.HashMap<String, Object>();
        // **冪等キーはこちらが作る**（サーバは採らない）。再送で二重に記録しない。
        body.put("activityId", UUID.randomUUID().toString());
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", type);
        body.put("unLocode", unLocode);
        body.put("voyageNumber", voyageNumber);
        body.put("consigneeName", consigneeName);
        body.put("completedAt", clock.instant().toString());
        return calls.post(StepRole.of(kind), "/api/v1/handling/activities", body);
    }
}
