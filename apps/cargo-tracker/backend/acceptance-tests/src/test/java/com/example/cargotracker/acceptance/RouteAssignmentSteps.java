package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 経路の確定（US09）のステップ定義。
 *
 * <p><b>画面を通さずに API を直接叩く。</b> 画面の検査を通さない経路でも集約が
 * 守っていることを見るため（デモ項目 7）。</p>
 */
public class RouteAssignmentSteps {

    /** 業務タイムゾーン。日付から日時を作るときに使う（UTC で作ると 1 日ずれる）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private final BookingRegistrationSteps bookings;

    private ResponseEntity<BookingRegistrationSteps.JsonMap> lastResponse;

    @Autowired
    public RouteAssignmentSteps(BookingRegistrationSteps bookings) {
        this.bookings = bookings;
    }

    private static Map<String, Object> leg(String voyageNumber, String from, String to,
            String loadDate, String unloadDate) {
        Map<String, Object> leg = new LinkedHashMap<>();
        leg.put("voyageNumber", voyageNumber);
        leg.put("loadUnLocode", from);
        leg.put("unloadUnLocode", to);
        leg.put("loadTime", LocalDate.parse(loadDate).atTime(9, 0).atZone(ZONE)
                .toInstant().toString());
        leg.put("unloadTime", LocalDate.parse(unloadDate).atTime(18, 0).atZone(ZONE)
                .toInstant().toString());
        return leg;
    }

    private ResponseEntity<BookingRegistrationSteps.JsonMap> assign(List<Map<String, Object>> legs) {
        Map<String, Object> row = bookings.currentBooking();
        assertThat(row).as("先に登録した予約が一覧に出ている").isNotNull();
        return bookings.rest().post()
                .uri(bookings.url("/api/v1/booking/bookings/" + row.get("bookingId") + "/route"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", "routing01")
                .body(Map.of("legs", legs))
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
    }

    @もし("その予約に {string} 発 {string} 着、到着 {string} の経路を確定する")
    public void 経路を確定する(String from, String to, String arrival) {
        lastResponse = assign(List.of(leg("V-ACC-001", from, to, "2026-11-01", arrival)));
    }

    @もし("その予約に {string} 経由 {string} から {string} 着、到着 {string} の経路を確定する")
    public void 経由つきで確定する(String from, String via, String to, String arrival) {
        lastResponse = assign(List.of(
                leg("V-ACC-001", from, via, "2026-11-01", "2026-11-10"),
                leg("V-ACC-002", via, to, "2026-11-11", arrival)));
    }

    @ならば("経路の確定は成功する")
    public void 確定は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("経路の確定は予約として断られる")
    public void 確定は業務規則で断られる() {
        // 業務規則違反（422）。状態の誤り（409）と分ける。利用者が「送る内容が悪い」のか
        // 「もうその段階ではない」のかを判断できるようにする。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @ならば("経路の確定は状態の誤りとして断られる")
    public void 確定は状態の誤りで断られる() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("ILLEGAL_STATE");
    }

    @かつ("{int} 秒以内にその予約の経路設定状態は {string} になる")
    public void 経路設定状態が変わる(int seconds, String label) {
        String expected = switch (label) {
            case "未設計" -> "NOT_ROUTED";
            case "設計依頼中" -> "ROUTING_REQUESTED";
            case "設計済" -> "ROUTED";
            case "誤配" -> "MISROUTED";
            default -> throw new IllegalArgumentException("知らない経路設定状態です: " + label);
        };
        SharedSteps.awaitWithin(seconds, () -> {
            Map<String, Object> row = bookings.currentBooking();
            return row != null && expected.equals(row.get("routingStatus"));
        }, "経路設定状態が「" + label + "」になる");
    }

    @かつ("その予約の旅程は {string} の順に読める")
    @SuppressWarnings("unchecked")
    public void 旅程が読める(String expected) {
        Map<String, Object> row = bookings.currentBooking();
        ResponseEntity<BookingRegistrationSteps.JsonMap> response = bookings.rest().get()
                .uri(bookings.url(
                        "/api/v1/booking/bookings/" + row.get("bookingId") + "/itinerary"))
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> legs =
                (List<Map<String, Object>>) response.getBody().get("legs");
        // 記録だけでは読めない。読み口が同じ順で返すことまで見る。
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < legs.size(); i++) {
            if (i == 0) {
                actual.append(legs.get(i).get("loadUnLocode"));
            }
            actual.append(" → ").append(legs.get(i).get("unloadUnLocode"));
        }
        assertThat(actual.toString()).isEqualTo(expected);
    }

    /**
     * 確定経路と予約番号を一緒に読めるか（US11 §受入基準 1）。
     *
     * <p><b>旅程の読み口は予約番号を返さない。</b> 経路設計者が読むのは予約詳細で、
     * そこに予約番号と旅程が並ぶ。旅程だけが読めても「どの予約の経路か」が分からず、
     * 荷主へのルート提案には使えない。</p>
     */
    @かつ("その予約の旅程は予約番号と一緒に読める")
    @SuppressWarnings("unchecked")
    public void 旅程が予約番号と一緒に読める() {
        Map<String, Object> row = bookings.currentBooking();
        assertThat(row).isNotNull();
        assertThat(row.get("bookingNumber")).as("予約番号が読める")
                .asString().startsWith("B-");

        ResponseEntity<BookingRegistrationSteps.JsonMap> response = bookings.rest().get()
                .uri(bookings.url(
                        "/api/v1/booking/bookings/" + row.get("bookingId") + "/itinerary"))
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody().get("legs"))
                .as("同じ予約 ID で旅程が読める").isNotEmpty();
    }

    @かつ("その予約の状態は {string} のままである")
    public void 予約の状態は変わらない(String label) {
        // 荷主に通知するまでは提案中（US12）。経路が付いただけで確定にしない。
        Map<String, Object> row = bookings.currentBooking();
        assertThat(row).isNotNull();
        assertThat(BookingRegistrationSteps.statusOf(label)).isEqualTo(row.get("bookingStatus"));
    }
}
