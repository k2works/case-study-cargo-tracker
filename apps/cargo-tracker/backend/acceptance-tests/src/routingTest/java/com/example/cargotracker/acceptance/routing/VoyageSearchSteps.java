package com.example.cargotracker.acceptance.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 航海スケジュールの検索（US07）のステップ。
 *
 * <p>港湾制約と経路探索は US08（IT5）。ここで確かめるのは航海スケジュール自身の
 * 条件（出発地・目的地・出発期間・貨物種別）だけである。</p>
 */
public class VoyageSearchSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int routingPort;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + routingPort + path;
    }

    private ResponseEntity<JsonMap> search(Map<String, String> criteria, boolean includeFinished) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString(url("/api/v1/routing/voyages"))
                .queryParam("page", 0)
                .queryParam("size", 200)
                .queryParam("includeFinished", includeFinished);
        criteria.forEach(uri::queryParam);
        return rest.get().uri(uri.build(true).toUri()).retrieve().toEntity(JsonMap.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> numbers(Map<String, String> criteria, boolean includeFinished) {
        JsonMap body = search(criteria, includeFinished).getBody();
        if (body == null || body.get("items") == null) {
            return List.of();
        }
        return ((List<Map<String, Object>>) body.get("items")).stream()
                .map(v -> String.valueOf(v.get("voyageNumber")))
                .toList();
    }

    private static Map<String, String> ports(String departure, String arrival) {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("departure", departure);
        criteria.put("arrival", arrival);
        return criteria;
    }

    private ResponseEntity<JsonMap> register(String voyageNumber, String from, String to,
            String departureAt, String arrivalAt) {
        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("departureUnLocode", from);
        movement.put("arrivalUnLocode", to);
        movement.put("departureAt", departureAt);
        movement.put("arrivalAt", arrivalAt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("voyageNumber", voyageNumber);
        body.put("carrierCode", "MOL");
        body.put("carrierName", "商船三井");
        body.put("vesselName", "MOL EXPRESS");
        body.put("movements", List.of(movement));
        body.put("acceptedCargoTypes", List.of("GENERAL"));
        return rest.post().uri(url("/api/v1/routing/voyages"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("出港済みの航海 {string} を {string} 発 {string} 着で登録する")
    public void 出港済みの航海を登録する(String voyageNumber, String from, String to) {
        register(voyageNumber, from, to, "2020-01-01T00:00:00Z", "2020-01-15T00:00:00Z");
    }

    @かつ("10 秒以内に出港済みを含む航海一覧に航海 {string} が現れる")
    public void 出港済みを含む一覧に現れる(String voyageNumber) {
        SharedRoutingSteps.awaitWithin(10,
                () -> numbers(Map.of(), true).contains(voyageNumber),
                "登録した航海が（出港済みを含む）一覧に出る");
    }

    @ならば("{string} 発 {string} 着で絞ると {string} は出るが {string} は出ない")
    public void 出発地と目的地で絞る(String departure, String arrival,
            String included, String excluded) {
        assertThat(numbers(ports(departure, arrival), false))
                .contains(included).doesNotContain(excluded);
    }

    @ならば("出発日が {string} から {string} の航海に {string} は出る")
    public void 期間で絞ると出る(String from, String to, String voyageNumber) {
        assertThat(numbers(period(from, to), false)).contains(voyageNumber);
    }

    @かつ("出発日が {string} から {string} の航海に {string} は出ない")
    public void 期間で絞ると出ない(String from, String to, String voyageNumber) {
        assertThat(numbers(period(from, to), false)).doesNotContain(voyageNumber);
    }

    private static Map<String, String> period(String from, String to) {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("departFrom", from + "T00:00:00Z");
        // 終了日はその日の終わりまで。00:00 で切ると、その日に出る便が落ちる。
        criteria.put("departTo", to + "T23:59:59Z");
        return criteria;
    }

    @ならば("{string} 発 {string} 着で絞った既定の一覧に {string} は出ない")
    public void 既定の絞り込みは消えない(String departure, String arrival, String voyageNumber) {
        // 条件で既定を置き換えると、出港済みが検索結果にだけ戻る。
        assertThat(numbers(ports(departure, arrival), false)).doesNotContain(voyageNumber);
    }

    @ならば("{string} 発 {string} 着で絞ると 0 件になる")
    public void ゼロ件になる(String departure, String arrival) {
        assertThat(numbers(ports(departure, arrival), false)).isEmpty();
    }

    @ならば("出発地 {string} で絞ると航海の検索は断られる")
    public void 港の書き方が誤り(String departure) {
        assertThat(search(Map.of("departure", departure), false)
                .getStatusCode().is4xxClientError())
                .as("0 件で返すと「その条件の航海が無い」と読める")
                .isTrue();
    }

    @ならば("貨物種別 {string} で絞ると航海の検索は断られる")
    public void 知らない種別(String cargoType) {
        assertThat(search(Map.of("cargoType", cargoType), false)
                .getStatusCode().is4xxClientError())
                .isTrue();
    }
}
