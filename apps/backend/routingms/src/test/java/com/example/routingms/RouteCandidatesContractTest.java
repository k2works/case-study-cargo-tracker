package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.routingms.application.internal.commandservices.RegisterVoyageCommand;
import com.example.routingms.application.internal.commandservices.RegisterVoyageUseCase;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.AuthenticatedUserFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 経路候補 API の契約（プロバイダ側・[ADR-019]）。
 *
 * <p>コンシューマ（bookingms の {@code RestRouteCandidateFinder}）が期待する
 * <strong>URL・クエリ・応答の項目名</strong>を、プロバイダ側でも固定する。
 *
 * <p><strong>片側だけの検査では守れない。</strong>コンシューマのテストはスタブ応答に対して
 * 緑になるため、プロバイダが項目名を変えても気づけない。ここが対になって初めて、
 * 「モックでは動くのに実物で落ちる」を捕まえられる。
 *
 * <p>期待する項目名は {@link #CONSUMER_EXPECTED_LEG_FIELDS} に列挙する。これは<strong>写し</strong>
 * であり、コンシューマ側では同じ名簿を DTO の要素から導いて突き合わせている
 * （{@code RestRouteCandidateFinderTest#rosterIsDerivedFromTheDto}）。コンシューマが項目を
 * 足すと向こうが赤になるので、そのとき<strong>同じ変更でここも直す</strong>。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("経路候補 API の契約（プロバイダ側）")
class RouteCandidatesContractTest {

    /** コンシューマ（bookingms）が区間から読む項目。増減したら両側を同じ変更で直す。 */
    private static final List<String> CONSUMER_EXPECTED_LEG_FIELDS = List.of(
            "voyageNumber", "fromUnLocode", "toUnLocode", "departureTime", "arrivalTime");

    /** コンシューマが応答の直下から読む項目。 */
    private static final String CONSUMER_EXPECTED_ROOT_FIELD = "candidates";

    /**
     * コンシューマ（bookingms）が名乗る主体。
     *
     * <p>BC をまたいで定数を共有しないため、項目名と同じく<strong>写して固定する</strong>。
     * コンシューマ側の {@code RestRouteCandidateFinder.SYSTEM_PRINCIPAL} を変えたら
     * ここも変わる。<strong>ロールは付かない</strong>。付くことを前提にした認可を
     * この API に足すと、コンシューマが断られる。
     */
    private static final String CONSUMER_PRINCIPAL = "system:bookingms";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RegisterVoyageUseCase registerVoyage;

    /**
     * 契約を確かめるための航海を、テスト自身が用意する。
     *
     * <p>種データに頼ると、種を変えたときに契約テストが理由の分からない形で落ちる。
     */
    @BeforeEach
    void givenVoyages() {
        // すでにあれば AlreadyExists が返るだけ。テストごとに作り直す必要は無い
        registerVoyage.register(new RegisterVoyageCommand(
                VoyageNumber.of("V-CONTRACT-1"), "契約丸", "契約海運", Set.of(CargoType.GENERAL),
                Schedule.of(List.of(
                        CarrierMovement.of(Location.of("JPTYO", "Tokyo"),
                                Location.of("CNSHA", "Shanghai"),
                                Instant.parse("2030-09-02T09:00:00Z"),
                                Instant.parse("2030-09-04T09:00:00Z")),
                        CarrierMovement.of(Location.of("CNSHA", "Shanghai"),
                                Location.of("USLAX", "Los Angeles"),
                                Instant.parse("2030-09-05T09:00:00Z"),
                                Instant.parse("2030-09-16T09:00:00Z"))))));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * <strong>[ADR-007] のフィルタを通す。</strong>
     *
     * <p>{@code webAppContextSetup} だけでは {@code FilterRegistrationBean} で登録した
     * フィルタが働かない。フィルタを通さない契約テストは、認証で断られる要求まで
     * 「契約どおり」と答える。実際 IT5 では、コンシューマが名乗らずに出していたのに
     * 両側のテストが緑のままで、実環境で経路を確定した瞬間にだけ 401 になった。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new AuthenticatedUserFilter())
                .build();
    }

    /**
     * <strong>コンシューマが実際に名乗る主体で出す。</strong>
     *
     * <p>テスト側で別の資格情報（利用者 ID とロール）を書くと、契約テストだけが通り、
     * 本物のコンシューマが断られる状態を素通りさせる。
     */
    private JsonNode getRoutes(String query) throws Exception {
        String body = mockMvc().perform(get("/api/v1/routes?" + query)
                        .header(AuthenticatedUser.USER_ID_HEADER, CONSUMER_PRINCIPAL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /**
     * 名乗らない要求は断る。
     *
     * <p>「名乗れば通る」だけを確かめると、フィルタを外しても緑のままになる。
     */
    @Test
    @DisplayName("名乗らない要求は 401 で断る")
    void rejectsUnidentifiedCaller() throws Exception {
        mockMvc().perform(get("/api/v1/routes?origin=JPTYO&destination=USLAX"
                        + "&deadline=2030-09-20&cargoType=GENERAL"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 名簿に無いサービスは通さない。
     *
     * <p>「system: で始まれば通す」形にすると、載せ忘れた主体ほど素通りする。
     */
    @Test
    @DisplayName("名簿に無いサービスを名乗っても 403 で断る")
    void rejectsUnknownServicePrincipal() throws Exception {
        mockMvc().perform(get("/api/v1/routes?origin=JPTYO&destination=USLAX"
                        + "&deadline=2030-09-20&cargoType=GENERAL")
                        .header(AuthenticatedUser.USER_ID_HEADER, "system:unknownms"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("コンシューマが送るクエリを受け付ける")
    void acceptsConsumerQuery() throws Exception {
        // ACL が組み立てるのと同じクエリ。項目が 1 つでも合わないと、実物では 400 になる
        JsonNode response = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL&maxTransshipments=3&earliestDeparture=2030-09-01");

        assertThat(response.has(CONSUMER_EXPECTED_ROOT_FIELD))
                .as("コンシューマが読む項目 %s が応答に無い", CONSUMER_EXPECTED_ROOT_FIELD)
                .isTrue();
    }

    @Test
    @DisplayName("区間はコンシューマが読む項目をすべて持つ")
    void legsCarryEveryFieldTheConsumerReads() throws Exception {
        JsonNode response = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL");

        JsonNode candidates = response.get(CONSUMER_EXPECTED_ROOT_FIELD);
        assertThat(candidates.isArray()).isTrue();
        assertThat(candidates)
                .as("契約を確かめるための候補が 1 件も無い。種データを確認すること")
                .isNotEmpty();

        JsonNode leg = candidates.get(0).get("legs").get(0);
        for (String field : CONSUMER_EXPECTED_LEG_FIELDS) {
            assertThat(leg.has(field))
                    .as("コンシューマが読む項目 %s が区間に無い", field)
                    .isTrue();
            assertThat(leg.get(field).isNull())
                    .as("コンシューマが読む項目 %s が null。旅程を組み立てられない", field)
                    .isFalse();
        }
    }

    /**
     * **パラメータが「効いている」ことまで確かめる。**
     *
     * <p>クエリはすべて任意なので、名前を変えても Spring は黙って null を渡す。項目の存在だけを
     * 見る検査は、改名しても緑のままになる（IT5 レビューの指摘）。結果が変わることで固定する。
     *
     * <p>件数ではなく<strong>このテストが用意した航海の有無</strong>で見る。同じ DB を
     * 他のテストも使うため、件数は他のテストの登録に左右される。
     */
    private boolean usesContractVoyage(JsonNode response) {
        for (JsonNode candidate : response.get(CONSUMER_EXPECTED_ROOT_FIELD)) {
            for (JsonNode leg : candidate.get("legs")) {
                if ("V-CONTRACT-1".equals(leg.get("voyageNumber").asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("到着期限が効く（名前を変えると結果が変わらなくなる）")
    void deadlineActuallyFilters() throws Exception {
        String base = "origin=JPTYO&destination=USLAX&cargoType=GENERAL";

        // V-CONTRACT-1 は 9/16 に着く。9/10 までを求めれば消える。
        // 他のテストが登録した航海が同じ DB にあるため、件数ではなくこの航海の有無で見る
        assertThat(usesContractVoyage(getRoutes(base + "&deadline=2030-09-20"))).isTrue();
        assertThat(usesContractVoyage(getRoutes(base + "&deadline=2030-09-10"))).isFalse();
    }

    @Test
    @DisplayName("貨物種別が効く")
    void cargoTypeActuallyFilters() throws Exception {
        String base = "origin=JPTYO&destination=USLAX&deadline=2030-09-20";

        // V-CONTRACT-1 は一般貨物しか運べない
        assertThat(usesContractVoyage(getRoutes(base + "&cargoType=GENERAL"))).isTrue();
        assertThat(usesContractVoyage(getRoutes(base + "&cargoType=HAZARDOUS"))).isFalse();
    }

    @Test
    @DisplayName("出発希望日が効く")
    void earliestDepartureActuallyFilters() throws Exception {
        String base = "origin=JPTYO&destination=USLAX&deadline=2030-09-20&cargoType=GENERAL";

        // V-CONTRACT-1 は 9/2 に出る。9/3 以降を求めれば消える
        assertThat(usesContractVoyage(getRoutes(base + "&earliestDeparture=2030-09-01"))).isTrue();
        assertThat(usesContractVoyage(getRoutes(base + "&earliestDeparture=2030-09-03"))).isFalse();
    }

    /**
     * 項目の「存在」だけでなく**型・形式**まで見る。
     *
     * <p>`departureTime` が ISO 8601 からエポックミリ秒に変わっても、存在だけを見る検査は
     * 緑のままで、コンシューマ（`Instant` として解釈する）は実物でだけ落ちる。
     */
    @Test
    @DisplayName("時刻は ISO 8601、航海番号は文字列で返す")
    void legFieldsHaveTheTypesTheConsumerParses() throws Exception {
        JsonNode leg = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL")
                .get(CONSUMER_EXPECTED_ROOT_FIELD).get(0).get("legs").get(0);

        assertThat(leg.get("voyageNumber").isTextual()).isTrue();
        assertThatCode(() -> Instant.parse(leg.get("departureTime").asText()))
                .doesNotThrowAnyException();
        assertThatCode(() -> Instant.parse(leg.get("arrivalTime").asText()))
                .doesNotThrowAnyException();
    }

    /**
     * コンシューマは `appliedCriteria` も読む（候補が 0 件のとき「何が効いているか」を出す）。
     *
     * <p>IT5 のレビューで、フロントの型が必須で宣言していた `earliestDeparture` が
     * サーバに無いことが分かった。契約の対象に入れる。
     */
    @Test
    @DisplayName("使った条件を返す（コンシューマが読む項目をすべて持つ）")
    void appliedCriteriaCarriesEveryFieldTheConsumerReads() throws Exception {
        JsonNode applied = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL&earliestDeparture=2030-09-01")
                .get("appliedCriteria");

        for (String field : List.of("originUnLocode", "originName", "destinationUnLocode",
                "destinationName", "arrivalDeadline", "cargoType", "maxTransshipments",
                "earliestDeparture")) {
            assertThat(applied.has(field))
                    .as("コンシューマが読む項目 %s が appliedCriteria に無い", field)
                    .isTrue();
        }
        assertThat(applied.get("earliestDeparture").isNull())
                .as("指定した出発希望日が返らない")
                .isFalse();
    }

    /**
     * `appliedCriteria` も<strong>型・形式</strong>まで見る（IT5 レビュー 中 13）。
     *
     * <p>IT5 の検査は区間の項目までしか型を見ておらず、入れ子はここだけ「存在」で
     * 止まっていた。存在だけの検査は、時刻がエポックミリ秒に変わっても、
     * 積み替えの上限が文字列で返るようになっても緑のままである。
     */
    @Test
    @DisplayName("使った条件は、コンシューマが解釈できる型で返す")
    void appliedCriteriaHasTheTypesTheConsumerParses() throws Exception {
        JsonNode applied = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL&earliestDeparture=2030-09-01")
                .get("appliedCriteria");

        for (String field : List.of("originUnLocode", "originName", "destinationUnLocode",
                "destinationName", "cargoType")) {
            assertThat(applied.get(field).isTextual())
                    .as("%s は文字列で返す", field)
                    .isTrue();
        }
        assertThat(applied.get("maxTransshipments").isInt())
                .as("積み替えの上限は数値で返す。文字列だと画面が比較できない")
                .isTrue();
        for (String field : List.of("arrivalDeadline", "earliestDeparture")) {
            assertThatCode(() -> Instant.parse(applied.get(field).asText()))
                    .as("%s は ISO 8601 で返す", field)
                    .doesNotThrowAnyException();
        }
    }

    /**
     * 使った条件が<strong>実際に送った値を映している</strong>ことまで確かめる。
     *
     * <p>存在と型だけを見る検査は、サーバがクエリを読み落として既定値を返していても緑になる。
     * そのとき画面は「積み替え 2 回まで（既定）で探しました」と表示するが、経路設計者は
     * 0 回を指定している。条件を緩める操作の起点が嘘になる。
     */
    @Test
    @DisplayName("使った条件は、送った値を映している（既定値で塗りつぶさない）")
    void appliedCriteriaEchoesWhatWasSent() throws Exception {
        JsonNode applied = getRoutes("origin=JPTYO&destination=CNSHA&deadline=2030-09-20"
                + "&cargoType=GENERAL&maxTransshipments=0&earliestDeparture=2030-09-01")
                .get("appliedCriteria");

        assertThat(applied.get("originUnLocode").asText()).isEqualTo("JPTYO");
        assertThat(applied.get("destinationUnLocode").asText()).isEqualTo("CNSHA");
        assertThat(applied.get("cargoType").asText()).isEqualTo("GENERAL");
        assertThat(applied.get("maxTransshipments").asInt())
                .as("送った積み替えの上限が既定値で塗りつぶされている")
                .isZero();
        // 地点名は対で返す（画面に対訳表を持たせない）
        assertThat(applied.get("originName").asText()).isNotBlank();
        assertThat(applied.get("destinationName").asText()).isNotBlank();
    }

    /**
     * 出発希望日を送らなければ `null` で返る。
     *
     * <p>省略時に既定値が入ると、画面は「出発希望日で絞っています」と示すことになり、
     * 経路設計者は指定していない条件を緩めようとする。
     */
    @Test
    @DisplayName("送らなかった出発希望日は null で返る")
    void appliedCriteriaKeepsOmittedEarliestDepartureNull() throws Exception {
        JsonNode applied = getRoutes("origin=JPTYO&destination=USLAX&deadline=2030-09-20"
                + "&cargoType=GENERAL")
                .get("appliedCriteria");

        assertThat(applied.get("earliestDeparture").isNull()).isTrue();
    }

    @Test
    @DisplayName("候補が無くても 200 と空の配列を返す（コンシューマは例外にしない）")
    void returnsEmptyArrayWhenNothingFound() throws Exception {
        JsonNode response = getRoutes("origin=JPTYO&destination=NLRTM&deadline=2030-09-20"
                + "&cargoType=GENERAL&maxTransshipments=0");

        assertThat(response.get(CONSUMER_EXPECTED_ROOT_FIELD).isArray()).isTrue();
    }
}
