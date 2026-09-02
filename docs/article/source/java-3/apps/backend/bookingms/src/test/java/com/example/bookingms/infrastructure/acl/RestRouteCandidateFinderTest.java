package com.example.bookingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateQuery;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateUnavailableException;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 経路候補の ACL（bookingms → routingms・[ADR-019]）。
 *
 * <p><strong>コンシューマ側の契約テスト</strong>である。ここで固定した期待（URL・クエリ・
 * 応答の形）を、プロバイダ側（routingms）の契約テストが同じ形で満たす。
 */
@DisplayName("経路候補の取得（ACL）")
class RestRouteCandidateFinderTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    /** マスタを何回読んだか。区間ごとに読み直していないことを数えるために持つ。 */
    private int locationReads;

    private final LocationRepository locations = new LocationRepository() {
        @Override
        public List<Location> findAll() {
            locationReads++;
            return List.of(TOKYO, BUSAN, LOS_ANGELES);
        }

        @Override
        public java.util.Map<String, String> regionsByUnLocode() {
            // この検査は地域区分を使わない。**空を返す**——使う経路が現れたら赤になる
            return java.util.Map.of();
        }

        @Override
        public Optional<Location> findByUnLocode(String unLocode) {
            locationReads++;
            return List.of(TOKYO, BUSAN, LOS_ANGELES).stream()
                    .filter(l -> l.unLocode().equals(unLocode)).findFirst();
        }

        @Override
        public Optional<ZoneId> timeZoneOf(String unLocode) {
            return Optional.of(ZoneId.of("Asia/Tokyo"));
        }
    };

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RestRouteCandidateFinder finder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://routingms:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestRouteCandidateFinder(builder.build(), locations);
    }

    /**
     * プロバイダ（routingms）の契約テストが<strong>写している</strong>名簿。
     *
     * <p>BC をまたいで定数を共有しないため、両側に同じ内容を置く。写しである以上、
     * 片側だけが増えうる。<strong>増えたことをここで赤にする</strong>のがこのテストの役目である。
     */
    private static final List<String> MIRRORED_ROOT_FIELDS = List.of("candidates");

    private static final List<String> MIRRORED_CANDIDATE_FIELDS = List.of("legs");

    private static final List<String> MIRRORED_LEG_FIELDS = List.of(
            "voyageNumber", "fromUnLocode", "toUnLocode", "departureTime", "arrivalTime");

    private static List<String> componentsOf(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }

    /**
     * <strong>名簿を手で書かない。</strong>DTO の要素から導いて写しと突き合わせる。
     *
     * <p>手書きの名簿は、コンシューマが DTO に項目を足しても赤にならない。足した項目を
     * プロバイダが返しているかは誰も確かめておらず、実物でだけ null になる（IT5 レビュー 中 14）。
     * ここが赤になったら、<strong>プロバイダ側の名簿も同じ変更で直す</strong>。
     */
    @Test
    @DisplayName("受け取る項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(componentsOf(RouteCandidateResponse.class))
                .as("応答の直下の項目が変わった。プロバイダ側の名簿も直すこと")
                .containsExactlyElementsOf(MIRRORED_ROOT_FIELDS);
        assertThat(componentsOf(RouteCandidateResponse.Candidate.class))
                .as("候補の項目が変わった。プロバイダ側の名簿も直すこと")
                .containsExactlyElementsOf(MIRRORED_CANDIDATE_FIELDS);
        assertThat(componentsOf(RouteCandidateResponse.CandidateLeg.class))
                .as("区間の項目が変わった。プロバイダ側の名簿も直すこと")
                .containsExactlyElementsOf(MIRRORED_LEG_FIELDS);
    }

    private static RouteCandidateQuery query(Integer maxTransshipments) {
        return new RouteCandidateQuery("JPTYO", "USLAX",
                LocalDate.of(2030, Month.SEPTEMBER, 20), CargoType.GENERAL, maxTransshipments,
                LocalDate.of(2030, Month.SEPTEMBER, 1), false);
    }

    private static final String TWO_LEGS = """
            {"candidates": [
              {"rank": 1, "legs": [
                {"voyageNumber": "V0201", "fromUnLocode": "JPTYO", "fromName": "Tokyo",
                 "toUnLocode": "KRPUS", "toName": "Busan",
                 "departureTime": "2030-09-02T09:00:00Z",
                 "arrivalTime": "2030-09-04T09:00:00Z"},
                {"voyageNumber": "V0202", "fromUnLocode": "KRPUS", "fromName": "Busan",
                 "toUnLocode": "USLAX", "toName": "Los Angeles",
                 "departureTime": "2030-09-05T09:00:00Z",
                 "arrivalTime": "2030-09-16T09:00:00Z"}
              ]}
            ]}
            """;

    @Test
    @DisplayName("条件をクエリに載せて問い合わせる")
    void sendsCriteriaAsQueryParameters() {
        server.expect(requestTo(Matchers.startsWith("http://routingms:8080/api/v1/routes")))
                .andExpect(queryParam("origin", "JPTYO"))
                .andExpect(queryParam("destination", "USLAX"))
                // 期限は日付のまま渡す。日時への変換は相手が業務タイムゾーンで行う（ADR-017）
                .andExpect(queryParam("deadline", "2030-09-20"))
                .andExpect(queryParam("cargoType", "GENERAL"))
                .andExpect(queryParam("maxTransshipments", "3"))
                .andExpect(queryParam("earliestDeparture", "2030-09-01"))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(query(3));

        server.verify();
    }

    /**
     * 誤配のあとの組み直し（US28-4・[ADR-026] 決定 4）。
     *
     * <p><strong>「期限で弾かない」を相手に伝える。</strong>routingms は既定で期限を超える
     * 候補を刈る。誤配した貨物は遅れているのが普通で、元の期限に間に合う便はまず残っていない
     * ——伝えなければ<strong>候補が 1 本も返らず、組み直す手段そのものが無くなる</strong>。
     * 集約から期限検査を外しただけでは、この経路には効かない。
     */
    @Test
    @DisplayName("再設計では、期限で弾かないことを相手に伝える")
    void tellsTheProviderNotToEnforceTheDeadlineWhenRerouting() {
        server.expect(requestTo(Matchers.startsWith("http://routingms:8080/api/v1/routes")))
                .andExpect(queryParam("reroute", "true"))
                // 期限そのものは渡す。**超える分を示す**ために要る（決定 5）
                .andExpect(queryParam("deadline", "2030-09-20"))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(new RouteCandidateQuery("JPTYO", "USLAX",
                LocalDate.of(2030, Month.SEPTEMBER, 20), CargoType.GENERAL, 3,
                LocalDate.of(2030, Month.SEPTEMBER, 1), true));

        server.verify();
    }

    /** 通常の割り当てでは伝えない。**緩めるのは再設計だけ**。 */
    @Test
    @DisplayName("通常の割り当てでは、期限で弾く既定のままにする")
    void keepsTheDeadlineForOrdinaryAssignment() {
        server.expect(requestTo(Matchers.startsWith("http://routingms:8080/api/v1/routes")))
                .andExpect(queryParam("reroute", "false"))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(query(3));

        server.verify();
    }

    @Test
    @DisplayName("応答を旅程へ変換する。地点はマスタから引く")
    void convertsResponseToItinerary() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        List<CargoItinerary> found = finder.find(query(null));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).legs()).hasSize(2);
        assertThat(found.get(0).origin()).isEqualTo(TOKYO);
        assertThat(found.get(0).destination()).isEqualTo(LOS_ANGELES);
        // 相手が返した名称ではなくマスタの名称。地点名の直しが 2 か所に分かれないようにする
        assertThat(found.get(0).legs().get(0).unloadLocation().name()).isEqualTo("Busan");
    }

    /**
     * 呼び出し元の利用者ではなく、<strong>システム自身</strong>を名乗る。
     *
     * <p>この呼び出しは「システムが経路候補を引く」ものであり、利用者の代理ではない。
     * 利用者を名乗ると、bookingms の中で完結する処理（確定時の再検証）が呼び出し元の
     * ロールに依存する。だからロールのヘッダは<strong>付けない</strong>。
     *
     * <p>一方、名乗らないと相手の [ADR-007] フィルタが 401 で断る。実際、IT5 では
     * 何も付けずに出しており、<strong>経路を確定する瞬間にだけ必ず失敗していた</strong>
     * （スタブ相手のテストはフィルタを通らないため、実環境まで誰も気づかなかった）。
     * サービス間の信頼はネットワーク境界（Gateway より内側）で担保する。
     */
    @Test
    @DisplayName("利用者ではなくシステム自身を名乗る（ロールは付けない）")
    void identifiesItselfAsSystemWithoutRoles() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER,
                        RestRouteCandidateFinder.SYSTEM_PRINCIPAL))
                .andExpect(headerDoesNotExist(AuthenticatedUser.ROLES_HEADER))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(query(null));

        server.verify();
    }

    /**
     * 地点マスタは<strong>1 回だけ</strong>読む（IT5 レビュー 低 33）。
     *
     * <p>区間ごとに読み直すと、確定 1 回あたり候補数 × 区間数 × 2（積込地と荷降し地）の
     * 問い合わせになる。候補が 10 件・3 区間なら 60 回である。
     *
     * <p><strong>回数を数える。</strong>結果だけを見る検査は、区間ごとに読み直す実装に
     * 戻しても緑のままで、遅くなったことを誰も知らせない。
     */
    @Test
    @DisplayName("地点マスタは候補の数によらず 1 回しか読まない")
    void readsTheLocationMasterOnce() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(query(null));

        assertThat(locationReads)
                .as("区間ごとに地点マスタを読み直している")
                .isEqualTo(1);
    }

    /**
     * 「確認できなかった」と「候補に無かった」を区別する（IT5 レビュー 高 9）。
     *
     * <p>相手の不調を空のリストにすると、呼び出し側は「航海スケジュールが変わった」と誤診し、
     * 経路設計者は何度探し直しても直らない作業に入る。
     */
    @Test
    @DisplayName("routingms が応答しなければ、空ではなく「確認できない」を返す")
    void reportsUnavailableWhenRoutingServiceFails() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withServerError());

        RouteCandidateQuery query = query(null);

        assertThatThrownBy(() -> finder.find(query))
                .isInstanceOf(RouteCandidateUnavailableException.class)
                .hasMessageContaining("いま経路を確認できません");
    }

    @Test
    @DisplayName("候補が無ければ空のリストを返す（例外にしない）")
    void returnsEmptyListWhenNoCandidate() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));

        // 「経路が無い」は業務上ありうる答えであり、失敗ではない
        assertThat(finder.find(query(null))).isEmpty();
    }

    /**
     * 相手が項目を足しても、こちらは壊れない。
     *
     * <p>知らない項目で失敗すると、routingms が候補に情報を 1 つ足しただけで
     * bookingms の確定が止まる。
     */
    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(TWO_LEGS.replace("{\"candidates\": [",
                        "{\"totalCount\": 1, \"appliedCriteria\": {\"maxTransshipments\": 2},"
                                + " \"candidates\": ["),
                        MediaType.APPLICATION_JSON));

        assertThat(finder.find(query(null))).hasSize(1);
    }

    /**
     * 知らない地点が混ざった候補は落とす。
     *
     * <p>変換できないものを黙って部分的に組み立てると、つながっていない旅程ができる。
     */
    @Test
    @DisplayName("マスタに無い地点を含む候補は落とす")
    void dropsCandidateWithUnknownPort() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(TWO_LEGS.replace("KRPUS", "XXXXX"),
                        MediaType.APPLICATION_JSON));

        assertThat(finder.find(query(null))).isEmpty();
    }
}
