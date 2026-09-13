package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 輸送中キャンセルの承認（US30 / UC22）のステップ。
 *
 * <p><b>{@code .feature} と同じ変更で書く。</b> IT14 では受け入れが 9 シナリオ
 * 書かれていたのにステップ定義が 0 件で、<b>1 つも回らないまま緑</b>だった。
 * 書いただけの受け入れは、何も検査しない。</p>
 *
 * <p><b>API を叩く。</b> 集約を直接呼ぶと「画面から見てどうなるか」を判別できない。</p>
 */
public class CancellationSteps {

    private final BookingRegistrationSteps bookings;

    /**
     * 結果の言葉（成功・断られる）を持っているステップ定義。
     *
     * <p><b>同じ判定を書き直さない。</b> 断り方の約束（409 か 422 か）が分かれると、
     * 片方だけが正しいまま残る。</p>
     */
    private final ConditionAndNotificationSteps outcomes;

    /**
     * 契約イベントを BC の入口から流すための Reaction Handler。
     *
     * <p><b>本番の入口である。</b> テスト専用の口ではない——handlingms が送る
     * イベントを、そのまま bookingms に渡している。</p>
     */
    private final com.example.cargotracker.booking.application.reaction
            .BookingReactionHandler reactions;

    @Autowired
    public CancellationSteps(BookingRegistrationSteps bookings,
            ConditionAndNotificationSteps outcomes,
            com.example.cargotracker.booking.application.reaction
                    .BookingReactionHandler reactions) {
        this.bookings = bookings;
        this.outcomes = outcomes;
        this.reactions = reactions;
    }

    private String bookingId() {
        Map<String, Object> row = bookings.currentBooking();
        assertThat(row).as("先に登録した予約が一覧に出ている").isNotNull();
        return String.valueOf(row.get("bookingId"));
    }

    private ResponseEntity<BookingRegistrationSteps.JsonMap> post(
            String path, Map<String, Object> body, String username) {
        var response = bookings.rest().post()
                .uri(bookings.url("/api/v1/booking/bookings/" + bookingId() + path))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", username)
                .body(body)
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
        outcomes.record(response);
        return response;
    }

    /**
     * 輸送中まで進める。
     *
     * <p><b>本番に裏口を作らない。</b> 「状態を直接書き換える口」を置くと、
     * 誰でも予約を配送完了にできてしまう。代わりに<b>契約イベントを BC の入口から
     * 流す</b>——handlingms が送るのと同じイベントで、そこから先（Reaction Handler →
     * 集約 → 投影）は本番と同じ道を通る。Axon の配送そのものは
     * {@code ContractEventRoundTripIT} が別に見ている。</p>
     *
     * <p><b>本筋でない段はなぞらない。</b> 確かめたいのはキャンセルの扱いで、
     * そこへ至る道は US06〜US15 が別の受け入れで固めている。</p>
     */
    @もし("その予約を輸送中にする")
    public void 輸送中にする() {
        トラッキングまで進める();
        reactions.on(new com.example.cargotracker.shared.contract.event
                .HandlingActivityRegisteredEvent("act-" + System.nanoTime(),
                trackingNumber(), bookingId(), "RECEIVE", "JPTYO", null, false, false,
                "handler01", java.time.Instant.parse("2026-09-20T01:00:00Z"),
                java.time.Instant.parse("2026-09-20T01:05:00Z")));
        SharedSteps.awaitWithin(10, () -> "IN_TRANSIT".equals(
                bookings.currentBooking().get("bookingStatus")), "予約が輸送中になる");
    }

    @もし("その予約を引取済にする")
    public void 引取済にする() {
        輸送中にする();
        reactions.on(new com.example.cargotracker.shared.contract.event.CargoDeliveredEvent(
                trackingNumber(), bookingId(),
                java.time.Instant.parse("2026-10-12T02:00:00Z"), "USNYC"));
        SharedSteps.awaitWithin(10, () -> "DELIVERED".equals(
                bookings.currentBooking().get("bookingStatus")), "予約が引取済になる");
    }

    /** 経路の確定から追跡番号の発行まで（キャンセルの前提。別の受け入れが固めている）。 */
    private void トラッキングまで進める() {
        post("/routing-request", Map.of(), "sales01");
        SharedSteps.awaitWithin(10, () -> "ROUTE_PROPOSED".equals(
                bookings.currentBooking().get("bookingStatus")), "経路提案中になる");
        Map<String, Object> leg = new LinkedHashMap<>();
        leg.put("voyageNumber", "V-MOL-001");
        leg.put("loadUnLocode", "JPTYO");
        leg.put("unloadUnLocode", "USNYC");
        leg.put("loadTime", "2026-09-20T00:00:00Z");
        leg.put("unloadTime", "2026-10-12T00:00:00Z");
        bookings.rest().post()
                .uri(bookings.url("/api/v1/booking/bookings/" + bookingId() + "/route"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", "routing01")
                .body(Map.of("legs", List.of(leg)))
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
        post("/notifications", Map.of("recipientEmail", "shipper@example.com",
                "summary", "JPTYO → USNYC"), "sales01");
        post("/confirmation", Map.of(), "sales01");
        post("/tracking-number", Map.of(), "routing01");
        SharedSteps.awaitWithin(10, () -> bookings.currentBooking().get("trackingNumber") != null,
                "追跡番号が付く");
    }

    private String trackingNumber() {
        return String.valueOf(bookings.currentBooking().get("trackingNumber"));
    }

    @もし("その予約を理由 {string} でキャンセルする")
    public void キャンセルする(String reason) {
        // **輸送開始前は即座に、輸送中は申請になる。** 入口は 1 つで、
        // どちらになるかは集約が状態から決める（判定を画面と集約の 2 か所に置かない）。
        post("/cancellation", Map.of("reason", reason), "sales01");
    }

    @もし("追跡管理者がその申請を陸揚げ地 {string} で承認する")
    public void 承認する(String dischargeUnLocode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dischargeUnLocode", dischargeUnLocode);
        post("/cancellation/approval", body, "tracker01");
    }

    @もし("追跡管理者がその申請を理由 {string} で却下する")
    public void 却下する(String reason) {
        post("/cancellation/rejection", Map.of("reason", reason), "tracker01");
    }

    @かつ("{int} 秒以内にその予約は承認待ちのキャンセル申請に理由 {string} で出る")
    public void 承認待ちに出る(int seconds, String reason) {
        SharedSteps.awaitWithin(seconds, () -> pendingRequest(reason) != null,
                "承認待ちのキャンセル申請に出る");
    }

    @かつ("{int} 秒以内にその予約は承認待ちのキャンセル申請に出ない")
    public void 承認待ちから消える(int seconds) {
        SharedSteps.awaitWithin(seconds, () -> pendingOfThisBooking() == null,
                "承認待ちのキャンセル申請から消える");
    }

    @かつ("その申請の判断は {string} で、陸揚げ地は {string} である")
    public void 判断と陸揚げ地(String decisionLabel, String dischargeUnLocode) {
        Map<String, Object> request = latestRequest();
        assertThat(request).as("申請の履歴が読めない").isNotNull();
        assertThat(request.get("decision")).isEqualTo(decisionOf(decisionLabel));
        assertThat(request.get("dischargeUnLocode")).isEqualTo(dischargeUnLocode);
    }

    @かつ("その申請の判断は {string} で、理由は {string} である")
    public void 判断と理由(String decisionLabel, String reason) {
        Map<String, Object> request = latestRequest();
        assertThat(request).as("申請の履歴が読めない").isNotNull();
        assertThat(request.get("decision")).isEqualTo(decisionOf(decisionLabel));
        assertThat(request.get("decisionReason")).isEqualTo(reason);
    }

    @ならば("{int} 秒以内にその予約のキャンセル履歴には申請者 {string} と判断者 {string} が残る")
    public void 履歴に残る(int seconds, String requestedBy, String decidedBy) {
        SharedSteps.awaitWithin(seconds, () -> {
            Map<String, Object> request = latestRequest();
            return request != null
                    && requestedBy.equals(request.get("requestedBy"))
                    && decidedBy.equals(request.get("decidedBy"))
                    && request.get("requestedAt") != null
                    && request.get("decidedAt") != null;
        }, "キャンセル履歴に申請者と判断者が残る");
    }

    /** 業務の言葉 → 投影の値。**列挙名を受け入れに書かない**（読む人は業務の言葉で読む）。 */
    private static String decisionOf(String label) {
        return switch (label) {
            case "承認" -> "APPROVED";
            case "却下" -> "REJECTED";
            default -> throw new IllegalArgumentException("知らない判断です: " + label);
        };
    }

    /** この予約の申請（新しい順の先頭）。履歴は予約詳細から読む。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> latestRequest() {
        var response = bookings.rest().get()
                .uri(bookings.url("/api/v1/booking/bookings/" + bookingId() + "/cancellation"))
                .header("X-Auth-Username", "sales01")
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return null;
        }
        var items = (List<Map<String, Object>>) response.getBody().get("items");
        return items == null || items.isEmpty() ? null : items.getFirst();
    }

    /** 承認待ちの一覧（S23）に出ているこの予約の申請。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingOfThisBooking() {
        var response = bookings.rest().get()
                .uri(bookings.url("/api/v1/booking/bookings/cancellations"))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return null;
        }
        var items = (List<Map<String, Object>>) response.getBody().get("items");
        String bookingId = bookingId();
        return items == null ? null : items.stream()
                .filter(item -> bookingId.equals(item.get("bookingId")))
                .findFirst().orElse(null);
    }

    private Map<String, Object> pendingRequest(String reason) {
        Map<String, Object> item = pendingOfThisBooking();
        return item != null && reason.equals(item.get("reason")) ? item : null;
    }
}
