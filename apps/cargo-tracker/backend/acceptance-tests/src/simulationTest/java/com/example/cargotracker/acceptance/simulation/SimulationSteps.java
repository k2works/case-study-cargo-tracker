package com.example.cargotracker.acceptance.simulation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 業務シミュレーションのデモ項目（US33・US34 / UC23）。
 *
 * <p><b>人と同じ経路だけを叩く。</b> ステップは Gateway の入口しか知らない——
 * 裏口から DB を覗くと、認可も連鎖も踏まないまま緑になる。</p>
 */
public class SimulationSteps {

    private String token;
    private String runId;
    private Response lastResponse;

    @前提("システム管理者 {string} でログインしている")
    public void ログインしている(String username) {
        SimulationStack.start();
        prepareVoyages();
        awaitQuiet();
        token = login(username);
    }

    /**
     * 走っている実行が終わるのを待つ。
     *
     * <p><b>シナリオは実行を残したまま終わることがある</b>（二重実行を確かめる
     * シナリオは、わざと 1 本走らせたまま断りを見る）。次のシナリオがそれに
     * ぶつかると「実行中です」で始められない——シナリオどうしを独立させるのは
     * 段取りの仕事で、守り（同時に 1 本）を緩める理由にはしない。</p>
     */
    private void awaitQuiet() {
        await("走っている実行が無くなる").atMost(Duration.ofSeconds(240))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> get("/api/v1/simulation/runs", "admin01")
                        .jsonPath().getList("items.status", String.class).stream()
                        .noneMatch("RUNNING"::equals));
    }

    @前提("シミュレーションが許可されていない設定である")
    public void 許可されていない設定である() {
        // **同じ実装を違う設定で立ててある。** 設定で決まる断りは、
        // 設定を変えた実物でしか確かめられない（US33 §4）。
        baseUrl = SimulationStack.disabledSimulationUrl();
    }

    private String baseUrl = null;

    private String target() {
        return baseUrl == null ? SimulationStack.gatewayUrl() : baseUrl;
    }

    @もし("シナリオ {string} を実行する")
    public void シナリオを実行する(String scenario) {
        lastResponse = given()
                .baseUri(target())
                .header("Authorization", "Bearer " + token)
                .header("X-Auth-Username", "admin01")
                .contentType("application/json")
                .body(Map.of("scenario", scenario))
                .post("/api/v1/simulation/runs");
        if (lastResponse.statusCode() == 201) {
            runId = lastResponse.jsonPath().getString("runId");
        }
        lastScenario = scenario;
    }

    private String lastScenario;

    @もし("同じシナリオをもう一度実行する")
    public void もう一度実行する() {
        シナリオを実行する(lastScenario);
    }

    @ならば("その実行は {int} 秒以内に {string} で終わる")
    public void 実行が終わる(int seconds, String statusLabel) {
        assertThat(runId).as("実行を始められていない: %s", lastResponse.asString()).isNotNull();
        await("実行が終わる").atMost(Duration.ofSeconds(seconds))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> !"実行中".equals(run().get("statusLabel")));
        assertThat(run().get("statusLabel"))
                .as("工程: %s", steps())
                .isEqualTo(statusLabel);
    }

    @ならば("その操作は断られる")
    public void 断られる() {
        assertThat(lastResponse.statusCode())
                .as("本文: %s", lastResponse.asString())
                .isGreaterThanOrEqualTo(400);
    }

    @かつ("断りに実行中の識別子が含まれる")
    public void 断りに識別子が含まれる() {
        // **「二重に実行できません」だけでは、いまの結果へ行けない。**
        assertThat(lastResponse.asString()).contains("SIM-");
    }

    @かつ("その実行の工程はすべて {string} である")
    public void 工程はすべて(String outcomeLabel) {
        assertThat(steps()).isNotEmpty()
                .allSatisfy(step -> assertThat(step.get("outcomeLabel")).isEqualTo(outcomeLabel));
    }

    @かつ("その実行は予約番号・追跡番号・請求番号を生成している")
    public void 識別子を生成している() {
        assertThat(producedOf("REGISTER_BOOKING")).isNotBlank();
        assertThat(producedOf("ISSUE_TRACKING_NUMBER")).isNotBlank();
        assertThat(producedOf("CALCULATE_INVOICE")).isNotBlank();
    }

    @かつ("生成された予約の状態は {string} である")
    public void 予約の状態は(String label) {
        Map<String, Object> booking = get("/api/v1/booking/bookings/"
                + producedOf("REGISTER_BOOKING"), "sales01").jsonPath().getMap("$");
        assertThat(String.valueOf(booking.get("bookingStatus"))).isEqualTo(codeOf(label));
    }

    @かつ("生成された請求書の状態は {string} である")
    public void 請求書の状態は(String label) {
        Map<String, Object> invoice = get("/api/v1/billing/invoices/"
                + producedOf("CALCULATE_INVOICE"), "accountant01").jsonPath().getMap("$");
        assertThat(String.valueOf(invoice.get("statusLabel"))).isEqualTo(label);
    }

    @かつ("営業の予約一覧に、生成された予約は出ない")
    public void 予約一覧に出ない() {
        List<String> ids = get("/api/v1/booking/bookings?page=0&size=200", "sales01")
                .jsonPath().getList("items.bookingId", String.class);
        assertThat(ids).doesNotContain(producedOf("REGISTER_BOOKING"));
    }

    @かつ("経理の請求一覧に、生成された請求書は出ない")
    public void 請求一覧に出ない() {
        List<String> ids = get("/api/v1/billing/invoices?page=0&size=200", "accountant01")
                .jsonPath().getList("items.invoiceId", String.class);
        assertThat(ids).doesNotContain(producedOf("CALCULATE_INVOICE"));
    }

    @かつ("要確認一覧に、生成された予約の項目は出ない")
    public void 要確認一覧に出ない() {
        List<String> targets = get("/api/v1/booking/attention-items", "sales01")
                .jsonPath().getList("items.targetId", String.class);
        assertThat(targets).doesNotContain(producedOf("REGISTER_BOOKING"));
    }

    @かつ("失敗した工程は {string} である")
    public void 失敗した工程は(String kindLabel) {
        assertThat(steps()).last()
                .satisfies(step -> {
                    assertThat(step.get("kindLabel")).isEqualTo(kindLabel);
                    assertThat(step.get("outcomeLabel")).isEqualTo("失敗");
                });
    }

    @かつ("その工程の理由に {string} が含まれる")
    public void 理由に含まれる(String fragment) {
        assertThat(String.valueOf(steps().get(steps().size() - 1).get("failureMessage")))
                .contains(fragment);
    }

    @かつ("それまでに作られた予約は残っている")
    public void 予約は残っている() {
        // **止まっても取り消さない**（US34 §3）。どこまで進んだかを追えるため。
        assertThat(get("/api/v1/booking/bookings/" + producedOf("REGISTER_BOOKING"), "sales01")
                .statusCode()).isEqualTo(200);
    }

    @かつ("どの工程も、前の工程が生成した識別子を使えている")
    public void 識別子を使えている() {
        // 前の工程の結果を使えていなければ、後続は 4xx で止まる——
        // 全部成功していることが「使えている」ことの証拠になる。
        工程はすべて("成功");
        assertThat(producedOf("CALCULATE_INVOICE"))
                .as("請求は連鎖が作る。引取まで届いていなければ読めない")
                .isNotBlank();
    }

    @かつ("工程の一覧に {string} と {string} が含まれる")
    public void 工程の一覧に含まれる(String first, String second) {
        List<String> labels = steps().stream()
                .map(step -> String.valueOf(step.get("kindLabel"))).toList();
        assertThat(labels).contains(first, second);
    }

    @かつ("どの工程にも所要時間が記録されている")
    public void 所要時間が記録されている() {
        assertThat(steps()).allSatisfy(step ->
                assertThat(step.get("elapsedMs")).isNotNull());
    }

    @かつ("実行の一覧にその実行が出る")
    public void 一覧に出る() {
        assertThat(recentRunIds()).contains(runId);
    }

    @かつ("一覧からその実行の工程を開ける")
    public void 一覧から開ける() {
        // **一覧から辿れることを見る。** 識別子を握って叩く形では、
        // 一覧に出ない実行でも緑になる。
        assertThat(recentRunIds()).contains(runId);
        assertThat(steps()).isNotEmpty();
    }

    private List<String> recentRunIds() {
        return get("/api/v1/simulation/runs", "admin01")
                .jsonPath().getList("items.runId", String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run() {
        return get("/api/v1/simulation/runs/" + runId, "admin01").jsonPath().getMap("$");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steps() {
        return (List<Map<String, Object>>) run().get("steps");
    }

    private String producedOf(String kind) {
        return steps().stream()
                .filter(step -> kind.equals(step.get("kind")))
                .map(step -> (String) step.get("producedId"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    private static String codeOf(String label) {
        return switch (label) {
            case "精算済" -> "SETTLED";
            case "配送完了" -> "DELIVERED";
            case "予約確定" -> "CONFIRMED";
            default -> throw new IllegalArgumentException("知らない状態です: " + label);
        };
    }

    private Response get(String path, String username) {
        return given()
                .baseUri(SimulationStack.gatewayUrl())
                .header("Authorization", "Bearer " + login(username))
                .header("X-Auth-Username", username)
                .get(path);
    }

    private static final Map<String, String> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    private String login(String username) {
        return TOKENS.computeIfAbsent(username, name -> {
            Response response = given()
                    .baseUri(SimulationStack.gatewayUrl())
                    .contentType("application/json")
                    .body(Map.of("username", name, "password", "secret1234"))
                    .post("/api/v1/auth/login");
            // **断られ方をそのまま見せる。** 本文を読めないまま落ちると、
            // 「JSON が壊れている」としか分からず原因に辿り着けない。
            assertThat(response.statusCode())
                    .as("ログインできない（%s）: %s", name, response.asString())
                    .isEqualTo(200);
            return response.jsonPath().getString("token");
        });
    }

    /**
     * 標準シナリオが通る便を用意する。
     *
     * <p><b>便が無ければ経路は組めない。</b> 立ち上げたばかりの routingms には
     * 航海が 1 本も無いので、確かめたい工程（精算まで）へ届かない。
     * <b>NO_ROUTE シナリオのために期限の近い便は作らない</b>——「候補が無い」
     * ことを期限の短さで作っているので、速い便があると止まらなくなる。</p>
     */
    private void prepareVoyages() {
        if (!VOYAGES_READY.compareAndSet(false, true)) {
            return;
        }
        Instant departure = Instant.now().plusSeconds(30L * 86_400);
        Response registered = given().baseUri(SimulationStack.gatewayUrl())
                .header("Authorization", "Bearer " + login("routing01"))
                .header("X-Auth-Username", "routing01")
                .contentType("application/json")
                .body(Map.of(
                        "voyageNumber", "V-SIM-001",
                        "carrierCode", "SIM",
                        "carrierName", "シミュレーション海運",
                        "vesselName", "SIM MARU",
                        "acceptedCargoTypes", List.of("GENERAL", "HAZARDOUS", "REEFER"),
                        "movements", List.of(Map.of(
                                "departureUnLocode", "JPTYO",
                                "arrivalUnLocode", "USNYC",
                                "departureAt", departure.toString(),
                                "arrivalAt", departure.plusSeconds(20L * 86_400).toString()))))
                .post("/api/v1/routing/voyages");
        // **登録の結果を捨てない。** 捨てると「便が読めない」としか分からず、
        // 断られた理由（入力の食い違い）に辿り着けない。
        assertThat(registered.statusCode())
                .as("便を登録できない: %s", registered.asString())
                .isBetween(200, 299);
        await("便が読めるようになる").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> get("/api/v1/routing/voyages/V-SIM-001", "routing01")
                        .statusCode() == 200);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean VOYAGES_READY =
            new java.util.concurrent.atomic.AtomicBoolean();
}
