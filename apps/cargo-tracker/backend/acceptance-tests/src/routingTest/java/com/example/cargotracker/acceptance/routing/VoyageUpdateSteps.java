package com.example.cargotracker.acceptance.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 航海スケジュールの更新（US25）のステップ。
 *
 * <p>routingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「経路設計者から見てどうなるか」を判別できない。</p>
 */
public class VoyageUpdateSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int routingPort;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + routingPort + path;
    }

    private static Map<String, Object> movement(String from, String to,
            String departureAt, String arrivalAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("departureUnLocode", from);
        m.put("arrivalUnLocode", to);
        m.put("departureAt", departureAt);
        m.put("arrivalAt", arrivalAt);
        return m;
    }

    private static Map<String, Object> body(String vesselName,
            List<Map<String, Object>> movements) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("carrierCode", "MOL");
        body.put("carrierName", "商船三井");
        body.put("vesselName", vesselName);
        body.put("movements", movements);
        body.put("acceptedCargoTypes", List.of("GENERAL"));
        return body;
    }

    private static List<Map<String, Object>> singleLeg() {
        return List.of(movement("JPTYO", "USNYC",
                "2026-09-10T09:00:00Z", "2026-09-24T18:00:00Z"));
    }

    private ResponseEntity<JsonMap> put(String voyageNumber, Map<String, Object> body) {
        return rest.put().uri(url("/api/v1/routing/voyages/" + voyageNumber))
                .contentType(MediaType.APPLICATION_JSON)
                // Gateway が載せるヘッダ。誰が直したかを投影に残す（US25）。
                .header("X-Auth-Username", "routing01")
                .body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    private JsonMap diff(String voyageNumber, Map<String, Object> body) {
        return rest.post().uri(url("/api/v1/routing/voyages/" + voyageNumber + "/diff"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonMap.class);
    }

    private Map<String, Object> detail(String voyageNumber) {
        return rest.get().uri(url("/api/v1/routing/voyages/" + voyageNumber))
                .retrieve().body(JsonMap.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> changesOf(JsonMap response) {
        assertThat(response).isNotNull();
        return (List<Map<String, Object>>) response.get("changes");
    }

    @もし("航海 {string} の船名を {string} に更新する")
    public void 船名を更新する(String voyageNumber, String vesselName) {
        lastResponse = put(voyageNumber, body(vesselName, singleLeg()));
    }

    @もし("航海 {string} を、港が繋がっていない寄港地で更新する")
    public void 繋がらない寄港地で更新する(String voyageNumber) {
        lastResponse = put(voyageNumber, body("MOL EXPRESS", List.of(
                movement("JPTYO", "SGSIN", "2026-09-10T09:00:00Z", "2026-09-16T08:00:00Z"),
                movement("USNYC", "GBLON", "2026-09-17T06:00:00Z", "2026-09-24T18:00:00Z"))));
    }

    @もし("航海 {string} を {string} 発 {string} 着の 1 区間に更新する")
    public void 一区間に更新する(String voyageNumber, String from, String to) {
        lastResponse = put(voyageNumber, body("MOL EXPRESS", List.of(
                movement(from, to, "2026-09-10T09:00:00Z", "2026-09-24T18:00:00Z"))));
    }

    @もし("航海 {string} の差分だけを確かめて更新はしない")
    public void 差分だけ確かめる(String voyageNumber) {
        // 「キャンセル」を選んだときの経路。差分の問い合わせに副作用が無いことを
        // 見る。副作用があると、確かめただけで既存が書き換わる。
        diff(voyageNumber, body("MOL VOYAGER", singleLeg()));
    }

    /**
     * 断りは更新のステップ側に置く。
     *
     * <p>Cucumber は glue のクラスごとに別のインスタンスを作るので、登録の
     * ステップが持つ応答はここからは見えない。同じ言葉のステップを共有すると、
     * 直前の登録の結果を見て緑になる（実際に 201 を見て通っていた）。</p>
     */
    @ならば("更新は航海として断られる")
    public void 更新は断られる() {
        assertThat(lastResponse.getStatusCode().is4xxClientError())
                .as("業務の拒否は 4xx で返る（実際は %s）", lastResponse.getStatusCode())
                .isTrue();
    }

    @ならば("受付は成功する")
    public void 受付は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("10 秒以内に航海 {string} の船名が {string} になる")
    public void 船名が変わる(String voyageNumber, String vesselName) {
        SharedRoutingSteps.awaitWithin(10,
                () -> vesselName.equals(detail(voyageNumber).get("vesselName")),
                "更新した船名が読める");
    }

    @ならば("航海 {string} の船名は {string} のままである")
    public void 船名は変わらない(String voyageNumber, String vesselName) {
        // **投影が追いつく時間を置いてから見る。** 直後に読むと「まだ反映されて
        // いないだけ」と区別が付かない。差分の問い合わせが更新まで送る実装に
        // 壊れても、その瞬間の値は古いままで緑になる。
        //
        // 「変わらないこと」は待っても確かめられないので、他のシナリオが更新の
        // 反映に使っている時間（10 秒の上限に対して実測 1 秒未満）より長く待つ。
        SharedRoutingSteps.awaitWithin(3,
                () -> "MOL VOYAGER".equals(detail(voyageNumber).get("vesselName")),
                "更新が反映されない（差分の問い合わせに副作用が無い）", false);
        assertThat(detail(voyageNumber).get("vesselName")).isEqualTo(vesselName);
        assertThat(detail(voyageNumber).get("updatedAt"))
                .as("確かめただけで最終更新が入るなら、差分の問い合わせに副作用がある")
                .isNull();
    }

    @ならば("航海 {string} には最終更新が残っている")
    public void 最終更新が残る(String voyageNumber) {
        // 誰がいつ直したかが残らないと、運航変更の反映を追えない。
        assertThat(detail(voyageNumber).get("updatedAt")).isNotNull();
        assertThat(detail(voyageNumber).get("updatedBy")).isNotNull();
    }

    @ならば("航海 {string} の船名を {string} にしたときの差分は {string} だけである")
    public void 差分は項目だけ(String voyageNumber, String vesselName, String label) {
        List<Map<String, Object>> changes =
                changesOf(diff(voyageNumber, body(vesselName, singleLeg())));
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).get("label")).isEqualTo(label);
    }

    @ならば("航海 {string} を同じ内容で送ったときの差分は空である")
    public void 差分は空(String voyageNumber) {
        assertThat(changesOf(diff(voyageNumber, body("MOL EXPRESS", singleLeg())))).isEmpty();
    }

    @ならば("10 秒以内に航海 {string} の寄港地は {string} の順に読める")
    public void 寄港地の順序を確かめる(String voyageNumber, String expectedRoute) {
        SharedRoutingSteps.awaitWithin(10,
                () -> expectedRoute.equals(routeOf(voyageNumber)),
                "更新後の寄港地が読める");
    }

    @SuppressWarnings("unchecked")
    private String routeOf(String voyageNumber) {
        Map<String, Object> voyage = detail(voyageNumber);
        List<Map<String, Object>> movements =
                (List<Map<String, Object>>) voyage.get("movements");
        if (movements == null || movements.isEmpty()) {
            return "";
        }
        List<String> ports = new java.util.ArrayList<>();
        ports.add(String.valueOf(movements.get(0).get("departureUnLocode")));
        movements.forEach(m -> ports.add(String.valueOf(m.get("arrivalUnLocode"))));
        return String.join(" → ", ports);
    }
}
