package com.example.bookingms.infrastructure.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateQuery;
import com.example.bookingms.application.port.RouteCandidateUnavailableException;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.LocalDate;
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

    private final LocationRepository locations = new LocationRepository() {
        @Override
        public List<Location> findAll() {
            return List.of(TOKYO, BUSAN, LOS_ANGELES);
        }

        @Override
        public Optional<Location> findByUnLocode(String unLocode) {
            return findAll().stream().filter(l -> l.unLocode().equals(unLocode)).findFirst();
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

    private static RouteCandidateQuery query(Integer maxTransshipments) {
        return new RouteCandidateQuery("JPTYO", "USLAX",
                LocalDate.of(2030, 9, 20), CargoType.GENERAL, maxTransshipments,
                LocalDate.of(2030, 9, 1));
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
     * [ADR-007] の利用者ヘッダは伝播しない。
     *
     * <p>この呼び出しは「システムが経路候補を引く」ものであり、利用者の代理ではない。
     * 伝播すると、bookingms の中で完結する処理（確定時の再検証）が呼び出し元のロールに依存する。
     */
    @Test
    @DisplayName("利用者ヘッダは伝播しない")
    void doesNotPropagateUserHeaders() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andExpect(headerDoesNotExist(AuthenticatedUser.USER_ID_HEADER))
                .andExpect(headerDoesNotExist(AuthenticatedUser.ROLES_HEADER))
                .andRespond(withSuccess(TWO_LEGS, MediaType.APPLICATION_JSON));

        finder.find(query(null));

        server.verify();
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

        assertThatThrownBy(() -> finder.find(query(null)))
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
