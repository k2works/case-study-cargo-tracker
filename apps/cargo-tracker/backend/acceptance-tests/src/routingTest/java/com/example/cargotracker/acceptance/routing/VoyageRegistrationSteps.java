package com.example.cargotracker.acceptance.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.前提;
import io.cucumber.java.ja.もし;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 航海スケジュールの登録（US24）と、対応貨物種別による絞り込み（US05）のステップ。
 *
 * <p>routingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「経路設計者から見てどうなるか」を判別できない。</p>
 */
public class VoyageRegistrationSteps {

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

    private ResponseEntity<JsonMap> register(String voyageNumber, String carrierName,
            String vesselName, List<Map<String, Object>> movements, List<String> cargoTypes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("voyageNumber", voyageNumber);
        body.put("carrierCode", "MOL");
        body.put("carrierName", carrierName);
        body.put("vesselName", vesselName);
        body.put("movements", movements);
        body.put("acceptedCargoTypes", cargoTypes);
        return rest.post().uri(url("/api/v1/routing/voyages"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> voyages(String cargoType) {
        String path = cargoType == null
                ? "/api/v1/routing/voyages"
                : "/api/v1/routing/voyages?cargoType=" + cargoType;
        JsonMap body = rest.get().uri(url(path)).retrieve().body(JsonMap.class);
        if (body == null || body.get("items") == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) body.get("items");
    }

    private Map<String, Object> find(String voyageNumber) {
        return voyages(null).stream()
                .filter(v -> voyageNumber.equals(v.get("voyageNumber")))
                .findFirst().orElse(null);
    }

    @前提("経路設計者 {string} でログインしている")
    public void ログインしている(String username) {
        // 認可は Gateway が担う。ここでは経路設計者として操作することだけを表す。
        assertThat(username).isNotBlank();
    }

    @もし("航海 {string} を運送会社 {string}、船名 {string}、{string} 発 {string} 着で登録する")
    public void 航海を登録する(String voyageNumber, String carrierName, String vesselName,
            String from, String to) {
        lastResponse = register(voyageNumber, carrierName, vesselName,
                List.of(movement(from, to, "2026-09-10T09:00:00Z", "2026-09-24T18:00:00Z")),
                List.of("GENERAL"));
    }

    @もし("航海 {string} を、{string} 発 {string} 着のあとに {string} 発 {string} 着を続けて登録する")
    public void 港が繋がらない航海を登録する(String voyageNumber, String from1, String to1,
            String from2, String to2) {
        lastResponse = register(voyageNumber, "商船三井", "MOL EXPRESS",
                List.of(movement(from1, to1, "2026-09-10T09:00:00Z", "2026-09-16T08:00:00Z"),
                        movement(from2, to2, "2026-09-17T06:00:00Z", "2026-09-24T18:00:00Z")),
                List.of("GENERAL"));
    }

    @もし("航海 {string} を、到着日時が出発日時より前になる寄港地で登録する")
    public void 到着が出発より前の航海を登録する(String voyageNumber) {
        lastResponse = register(voyageNumber, "商船三井", "MOL EXPRESS",
                List.of(movement("JPTYO", "USNYC", "2026-09-24T18:00:00Z", "2026-09-10T09:00:00Z")),
                List.of("GENERAL"));
    }

    @もし("航海 {string} を対応貨物種別を選ばずに登録する")
    public void 種別を選ばず登録する(String voyageNumber) {
        lastResponse = register(voyageNumber, "商船三井", "MOL EXPRESS",
                List.of(movement("JPTYO", "USNYC", "2026-09-10T09:00:00Z", "2026-09-24T18:00:00Z")),
                List.of());
    }

    @もし("航海 {string} を対応貨物種別 {string} で登録する")
    public void 種別を指定して登録する(String voyageNumber, String cargoTypes) {
        lastResponse = register(voyageNumber, "商船三井", "MOL EXPRESS",
                List.of(movement("JPTYO", "USNYC", "2026-09-10T09:00:00Z", "2026-09-24T18:00:00Z")),
                Arrays.asList(cargoTypes.split(",")));
    }

    @ならば("受付は成功し、航海番号が返る")
    public void 受付は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(lastResponse.getBody()).isNotNull();
        assertThat(String.valueOf(lastResponse.getBody().get("voyageNumber"))).isNotBlank();
    }

    @ならば("受付は航海として断られる")
    public void 受付は断られる() {
        // 500 で落ちるのと業務として断るのは別。断りは 4xx で返る。
        assertThat(lastResponse.getStatusCode().is4xxClientError())
                .as("業務の拒否は 4xx で返る（実際は %s）", lastResponse.getStatusCode())
                .isTrue();
    }

    @かつ("10 秒以内に航海一覧に航海 {string} が現れる")
    public void 一覧に現れる(String voyageNumber) {
        SharedRoutingSteps.awaitWithin(10, () -> find(voyageNumber) != null,
                "登録した航海が一覧に出る");
    }

    @かつ("その航海の船名は {string} である")
    public void 船名を確かめる(String vesselName) {
        assertThat(find("V-MOL-001")).isNotNull();
        assertThat(find("V-MOL-001").get("vesselName")).isEqualTo(vesselName);
    }

    @ならば("その航海の対応貨物種別は {string} だけである")
    public void 対応貨物種別を確かめる(String cargoType) {
        Map<String, Object> voyage = find("V-MOL-005");
        assertThat(voyage).isNotNull();
        assertThat(voyage.get("acceptedCargoTypes")).isEqualTo(List.of(cargoType));
    }

    @ならば("貨物種別 {string} で絞った航海一覧に {string} は出るが {string} は出ない")
    public void 種別で絞る(String cargoType, String included, String excluded) {
        List<String> numbers = voyages(cargoType).stream()
                .map(v -> String.valueOf(v.get("voyageNumber"))).toList();
        assertThat(numbers).contains(included).doesNotContain(excluded);
    }
}
