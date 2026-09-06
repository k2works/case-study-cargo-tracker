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
 * 条件の調整・差し戻し（US10）と、荷主への通知・経路設計への戻し（US12）。
 *
 * <p><b>画面を通さずに API を直接叩く。</b> 画面の検査を通さない経路でも集約が
 * 守っていることを見る（デモ項目 4）。</p>
 */
public class ConditionAndNotificationSteps {

    private final BookingRegistrationSteps bookings;

    private ResponseEntity<BookingRegistrationSteps.JsonMap> lastResponse;

    @Autowired
    public ConditionAndNotificationSteps(BookingRegistrationSteps bookings) {
        this.bookings = bookings;
    }

    private String bookingId() {
        Map<String, Object> row = bookings.currentBooking();
        assertThat(row).as("先に登録した予約が一覧に出ている").isNotNull();
        return String.valueOf(row.get("bookingId"));
    }

    private ResponseEntity<BookingRegistrationSteps.JsonMap> post(
            String path, Map<String, Object> body, String username) {
        return bookings.rest().post()
                .uri(bookings.url("/api/v1/booking/bookings/" + bookingId() + path))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", username)
                .body(body)
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
    }

    @もし("その予約の到着期限を {string} に、除外港を {string} にして条件を調整する")
    public void 条件を調整する(String deadline, String excluded) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("arrivalDeadline", deadline);
        body.put("excludeUnLocodes", excluded.isBlank() ? List.of() : List.of(excluded.split(",")));
        lastResponse = bookings.rest().put()
                .uri(bookings.url("/api/v1/booking/bookings/" + bookingId() + "/route-specification"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", "routing01")
                .body(body)
                .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
    }

    @もし("その予約を理由 {string} で営業へ差し戻す")
    public void 営業へ差し戻す(String reason) {
        lastResponse = post("/condition-review", Map.of("reason", reason), "routing01");
    }

    @もし("その予約を宛先 {string} 内容 {string} で荷主へ通知する")
    public void 荷主へ通知する(String email, String summary) {
        lastResponse = post("/notifications",
                Map.of("recipientEmail", email, "summary", summary), "sales01");
    }

    @もし("その予約を理由 {string} で経路設計へ戻す")
    public void 経路設計へ戻す(String reason) {
        lastResponse = post("/return-to-routing", Map.of("reason", reason), "sales01");
    }

    @ならば("その操作は成功する")
    public void 操作は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("その操作は状態の誤りとして断られる")
    public void 状態の誤りで断られる() {
        // 画面のボタンを出さないだけでは守りにならない。API を直接叩いても断る。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @ならば("その操作は入力の誤りとして断られる")
    public void 入力の誤りで断られる() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @かつ("{int} 秒以内にその予約の到着期限は {string} になる")
    public void 到着期限が変わる(int seconds, String expected) {
        SharedSteps.awaitWithin(seconds, () -> {
            Map<String, Object> row = bookings.currentBooking();
            return row != null && expected.equals(String.valueOf(row.get("arrivalDeadline")));
        }, "到着期限が " + expected + " になる");
    }

    @かつ("{int} 秒以内にその予約は営業の見直し依頼に理由 {string} で出る")
    public void 見直し依頼に出る(int seconds, String reason) {
        // **件数だけでは仕事が進まない。** 理由が読めることまで見る。
        // **投影を待つ。** 他のステップは待っているのに、ここだけ即座に照会して
        // いた（IT6 レビュー 中）。速いから緑なだけで、CI が混むと落ちる。
        String id = bookingId();
        SharedSteps.awaitWithin(seconds, () -> {
            ResponseEntity<BookingRegistrationSteps.JsonMap> response = bookings.rest().get()
                    .uri(bookings.url("/api/v1/booking/bookings/condition-reviews"))
                    .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
            return response.getStatusCode() == HttpStatus.OK
                    && response.getBody().get("items").toString().contains(id)
                    && response.getBody().get("items").toString().contains(reason);
        }, "見直し依頼に理由「" + reason + "」で出る");
    }

    @かつ("{int} 秒以内にその予約の通知履歴には内容 {string} が {int} 件ある")
    @SuppressWarnings("unchecked")
    public void 通知履歴を数える(int seconds, String summary, int expected) {
        // **投影を待つ。** 通知の 200 を確かめた直後に数えると、投影が追いつく前に
        // 読んで 0 件で落ちる（IT6 レビュー 中）。
        String id = bookingId();
        SharedSteps.awaitWithin(seconds, () -> {
            ResponseEntity<BookingRegistrationSteps.JsonMap> response = bookings.rest().get()
                    .uri(bookings.url("/api/v1/booking/bookings/" + id + "/notifications"))
                    .retrieve().toEntity(BookingRegistrationSteps.JsonMap.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                return false;
            }
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            return items.stream().filter(item -> summary.equals(item.get("summary")))
                    .count() == expected;
        }, "通知履歴に「" + summary + "」が " + expected + " 件ある");
    }
}
