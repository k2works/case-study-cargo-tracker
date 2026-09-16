package com.example.cargotracker.simulation.infrastructure.api;

import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.businessReason;
import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.parse;

import com.example.cargotracker.simulation.application.BusinessApi.StepResult;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * その輸送に要る便を用意する（**必要なデータ準備もシナリオに含める**）。
 *
 * <p><b>環境に溜まった便を当てにしない。</b> 便が 1 本も無い環境（作り直した
 * 直後のクラスタ）では、どのシナリオも「経路の確定」で止まる——確かめたいのは
 * 業務の連鎖であって、環境の品揃えではない。</p>
 *
 * <p><b>あるものは作り直さない。</b> 便は荷主に属さないので印を付けられず
 * （[ADR-0020] 決定 4）、航海スケジュールの一覧に本物として並ぶ。毎回登録すると
 * 流すほど一覧が埋まるので、<b>同じ区間を同じ貨物種別で運べる便がすでにあれば
 * 足さない</b>（作る前に探す）。</p>
 *
 * <p><b>正常系の担い手とは別のクラスにする。</b> 1 つのファイルに積むと
 * 読みどころが埋もれ、行の上限にも当たる。</p>
 */
final class GatewayVoyages {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    /**
     * 誤配の記録先の候補。
     *
     * <p>3 つあれば足りる——出発地と目的地は高々 2 つなので、3 つ目の港は必ず
     * どちらとも違う。</p>
     */
    private static final List<String> OFF_ROUTE_PORTS = List.of("SGSIN", "NLRTM", "DEHAM");

    private GatewayVoyages() {
    }

    /**
     * 用意する。
     *
     * @return 足した便の番号（何も足さなければ、そう言う文言）
     */
    static StepResult prepare(GatewayCalls calls, ScenarioInput input, Clock clock, String tag) {
        StepRole role = StepRole.of(StepKind.PREPARE_VOYAGES);
        var existing = calls.get(role, "/api/v1/routing/voyages?size=200");
        if (!existing.ok()) {
            return StepResult.failure(existing.status(), businessReason(existing));
        }
        JsonNode voyages = parse(existing).path("items");
        List<String> prepared = new ArrayList<>();
        for (String[] leg : neededLegs(input)) {
            if (servedBy(voyages, input.cargoType(), leg[0], leg[1])) {
                continue;
            }
            StepResult registered =
                    register(calls, input, clock, tag, leg[0], leg[1], prepared.size());
            if (!registered.succeeded()) {
                return registered;
            }
            prepared.add(registered.producedId());
        }
        // **何も足さなかったことも記録に残す。** 空で返すと、読む人は
        // 「用意したのか、既にあったのか」を区別できない。
        return StepResult.success(prepared.isEmpty()
                ? "既にある便を使います" : String.join(",", prepared));
    }

    /**
     * 用意しておきたい区間。
     *
     * <p><b>NO_ROUTE でも用意する。</b> 便がある中で「その港だけ便が無い」ほうが、
     * 確かめたい断り（US34）に近い——便が 1 本も無いから止まったのでは、何を
     * 確かめたのか分からない。</p>
     *
     * <p><b>誤配のための区間も用意する</b>（{@link Scenario#MISROUTE}）。
     * 組み直しは<b>現在地から</b>行うので、経路外の港から目的地へ運べる便が
     * 無ければ「組み直す先がない」で止まる。</p>
     */
    private static List<String[]> neededLegs(ScenarioInput input) {
        String origin = input.originUnLocode();
        String destination = input.destinationUnLocode();
        List<String[]> legs = new ArrayList<>();
        legs.add(new String[] {origin, destination});
        if (input.scenario() == Scenario.MISROUTE) {
            legs.add(new String[] {offRoutePort(origin, destination), destination});
        }
        return legs;
    }

    /**
     * 誤配の記録先になる港。
     *
     * <p><b>出発地でも目的地でもない港を、決まった順で選ぶ。</b> 乱数で選ぶと、
     * 同じ種から同じ並びを作れなくなる（US36 §受入基準 3）。</p>
     */
    private static String offRoutePort(String origin, String destination) {
        for (String port : OFF_ROUTE_PORTS) {
            if (!port.equals(origin) && !port.equals(destination)) {
                return port;
            }
        }
        throw new IllegalStateException("経路外の港を選べません: " + origin + " -> " + destination);
    }

    /**
     * その区間を、この貨物種別で運べる便があるか。
     *
     * <p><b>貨物種別まで見る。</b> 便は受け入れる種別を宣言しており、宣言が空なら
     * 一般貨物だけを運ぶ（`Voyage` の不変条件 4）——区間だけで探すと、危険物を
     * 運べない便を見つけて「用意できた」と判断してしまう。</p>
     */
    private static boolean servedBy(JsonNode voyages, String cargoType,
            String departure, String arrival) {
        for (JsonNode voyage : voyages) {
            if (!accepts(voyage, cargoType)) {
                continue;
            }
            for (JsonNode movement : voyage.path("movements")) {
                if (departure.equals(movement.path("departureUnLocode").asText())
                        && arrival.equals(movement.path("arrivalUnLocode").asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean accepts(JsonNode voyage, String cargoType) {
        JsonNode accepted = voyage.path("acceptedCargoTypes");
        if (!accepted.isArray() || accepted.isEmpty()) {
            // **宣言が空なら一般貨物だけ**（`Voyage` の不変条件 4）。
            return "GENERAL".equals(cargoType);
        }
        for (JsonNode type : accepted) {
            if (cargoType.equals(type.asText())) {
                return true;
            }
        }
        return false;
    }

    private static StepResult register(GatewayCalls calls, ScenarioInput input, Clock clock,
            String tag, String departure, String arrival, int index) {
        // **出港は先の日付にする。** 過ぎた便は候補に出ない。到着期限は 30 日
        // 以上先なので、2 日後に出て 4 日後に着く便は必ず間に合う。
        LocalDate now = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var body = new LinkedHashMap<String, Object>();
        body.put("voyageNumber", "V-SIM-" + tag + "-" + index);
        body.put("carrierCode", "SIM");
        body.put("carrierName", "シミュレーション汽船");
        body.put("vesselName", "SIMULATION " + tag);
        body.put("movements", List.of(Map.of(
                "departureUnLocode", departure,
                "arrivalUnLocode", arrival,
                "departureAt", now.plusDays(2) + "T00:00:00Z",
                "arrivalAt", now.plusDays(4) + "T00:00:00Z")));
        // **運ぶ貨物の種別を宣言する。** 宣言しないと一般貨物だけを運ぶ便になり、
        // 危険物・冷凍の実行は候補 0 件で止まる。
        body.put("acceptedCargoTypes", List.of(input.cargoType()));
        var response = calls.post(StepRole.of(StepKind.PREPARE_VOYAGES),
                "/api/v1/routing/voyages", body);
        return response.ok()
                ? StepResult.success(body.get("voyageNumber").toString())
                : StepResult.failure(response.status(), businessReason(response));
    }
}
