package com.example.cargotracker.acceptance.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindOverdueInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceListView;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 請求書の発行・入金・取消（US23）のデモ項目。
 *
 * <p><b>前提は {@link BillingSteps} と共有する。</b> 貨物と荷主の写しを作る手順は
 * 同じで、ここから先（発行・入金・取消）だけが違う。同じ表現を二度定義すると
 * Cucumber が断るので、ここには<b>この feature にしか出てこない文</b>だけを置く。</p>
 *
 * <p><b>「今日」をずらして期限を確かめる</b>（不変条件 4）。超過は列に持たず
 * 問い合わせのたびに数えるので、時計を進めずに境界を踏める。期限当日と翌日の
 * 両方を置く——片方だけだと {@code <=} と {@code <} の取り違えが素通りする。</p>
 */
public class SettlementSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private BillingSteps billing;

    @Autowired
    private QueryDispatcher queries;

    @Autowired
    private InvoiceMapper invoices;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    /** 未払いを数えるときの「今日」。シナリオがずらす。 */
    private LocalDate today;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/billing/invoices" + path;
    }

    private Map<String, Object> invoice() {
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(url("/" + invoiceId())).retrieve().toEntity(JSON);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    private String invoiceId() {
        return billing.invoiceId();
    }

    private LocalDate dueOn() {
        return LocalDate.parse(String.valueOf(invoice().get("dueOn")));
    }

    // **同じ文を もし／かつ の両方で使う。** 注釈は 1 つだけにする。
    @もし("その請求書を発行する")
    public void 発行する() {
        billing.record(rest.post().uri(url("/" + invoiceId() + "/issue"))
                .header("X-Auth-Username", "accountant01")
                .retrieve().toBodilessEntity());
    }

    @もし("その請求書をもう一度発行する")
    public void もう一度発行する() {
        発行する();
    }

    @もし("その請求書の入金を記録する")
    public void 入金を記録する() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", new BigDecimal(String.valueOf(invoice().get("totalAmount"))));
        body.put("paidAt", Instant.now().toString());
        billing.record(rest.post().uri(url("/" + invoiceId() + "/payments"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity());
    }

    @もし("理由 {string} でその請求書を取り消す")
    public void 取り消す(String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        billing.record(rest.post().uri(url("/" + invoiceId() + "/void"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity());
    }

    @ならば("その操作は断られる")
    public void 操作は断られる() {
        assertThat(billing.lastStatus().is2xxSuccessful())
                .as("断るはずの操作が通った（%s）", billing.lastStatus())
                .isFalse();
    }

    @かつ("支払期限は発行日の {int} 日後である")
    public void 支払期限は発行日の日後(int days) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> invoice = invoice();
            assertThat(invoice.get("issuedOn")).as("発行日が残っていない").isNotNull();
            assertThat(LocalDate.parse(String.valueOf(invoice.get("dueOn"))))
                    .isEqualTo(LocalDate.parse(String.valueOf(invoice.get("issuedOn")))
                            .plusDays(days));
        });
    }

    @もし("支払期限の翌日になる")
    public void 支払期限の翌日になる() {
        await().atMost(Duration.ofSeconds(30)).until(() -> invoice().get("dueOn") != null);
        this.today = dueOn().plusDays(1);
    }

    @もし("支払期限の当日になる")
    public void 支払期限の当日になる() {
        await().atMost(Duration.ofSeconds(30)).until(() -> invoice().get("dueOn") != null);
        this.today = dueOn();
    }

    @ならば("その請求書は未払いとして出る")
    public void 未払いとして出る() {
        assertThat(未払いの請求番号()).contains(invoiceId());
    }

    @ならば("その請求書は未払いとして出ない")
    public void 未払いとして出ない() {
        assertThat(未払いの請求番号())
                .as("期限当日は超過ではない（不変条件 4）")
                .doesNotContain(invoiceId());
    }

    private java.util.List<String> 未払いの請求番号() {
        // **画面が読む問い合わせを踏む。** 絞りは SQL 側にあるので、Java の
        // 述語だけを見ると境界の取り違えが素通りする。
        InvoiceListView view = queries.query(new FindOverdueInvoicesQuery(today),
                InvoiceListView.class);
        return view.items().stream().map(item -> item.invoiceId()).toList();
    }

    @ならば("{int} 秒以内に発行の通知が記録される")
    public void 通知が記録される(int seconds) {
        await().atMost(Duration.ofSeconds(seconds + 20)).untilAsserted(() ->
                assertThat(invoices.findNotification(invoiceId())).isNotNull());
    }

    @かつ("通知の宛先はその予約の荷主である")
    public void 通知の宛先は荷主() {
        assertThat(invoices.findNotification(invoiceId()).shipperId())
                .isEqualTo(billing.shipperId());
    }
}
