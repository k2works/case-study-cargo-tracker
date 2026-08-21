package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.routingms.application.internal.RegisterVoyageCommand;
import com.example.routingms.application.internal.RegisterVoyageUseCase;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import com.example.shared.auth.AuthenticatedUser;
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
 * <p>期待する項目名は {@link #CONSUMER_EXPECTED_LEG_FIELDS} に列挙する。コンシューマ側の
 * DTO を変えたらここも変わる（変えないと赤になる）。
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

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private JsonNode getRoutes(String query) throws Exception {
        String body = mockMvc().perform(get("/api/v1/routes?" + query)
                        .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
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

    @Test
    @DisplayName("候補が無くても 200 と空の配列を返す（コンシューマは例外にしない）")
    void returnsEmptyArrayWhenNothingFound() throws Exception {
        JsonNode response = getRoutes("origin=JPTYO&destination=NLRTM&deadline=2030-09-20"
                + "&cargoType=GENERAL&maxTransshipments=0");

        assertThat(response.get(CONSUMER_EXPECTED_ROOT_FIELD).isArray()).isTrue();
    }
}
