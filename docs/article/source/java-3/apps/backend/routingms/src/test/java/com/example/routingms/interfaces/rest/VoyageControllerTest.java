package com.example.routingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.routingms.application.internal.commandservices.RegisterVoyageUseCase;
import com.example.routingms.application.internal.queryservices.SearchVoyageUseCase;
import com.example.routingms.application.internal.commandservices.VoyageOutcome;
import com.example.routingms.domain.repository.LocationRepository;
import com.example.routingms.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.routingms.domain.model.valueobjects.CargoType;
import com.example.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageDifference;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VoyageController.class)
@DisplayName("航海スケジュールの API")
class VoyageControllerTest {

    private static final String BODY = """
            {
              "voyageNumber": "V0100",
              "vesselName": "さくら丸",
              "carrierName": "日本郵船",
              "supportedCargoTypes": ["GENERAL"],
              "movements": [
                {
                  "departureUnLocode": "JPTYO",
                  "arrivalUnLocode": "USLAX",
                  "departureTime": "2026-10-01T09:00:00Z",
                  "arrivalTime": "2026-10-18T12:00:00Z"
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterVoyageUseCase registerVoyage;

    @MockitoBean
    private SearchVoyageUseCase searchVoyage;

    @MockitoBean
    private LocationRepository locations;

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private static Voyage voyage(String vesselName) {
        return Voyage.register(VoyageNumber.of("V0100"), vesselName, "日本郵船",
                Set.of(CargoType.GENERAL),
                Schedule.of(List.of(CarrierMovement.of(TOKYO, LOS_ANGELES,
                        Instant.parse("2026-10-01T09:00:00Z"),
                        Instant.parse("2026-10-18T12:00:00Z")))));
    }

    @BeforeEach
    void stubLocations() {
        when(locations.findByUnLocode("JPTYO")).thenReturn(Optional.of(TOKYO));
        when(locations.findByUnLocode("USLAX")).thenReturn(Optional.of(LOS_ANGELES));
    }

    @Nested
    @DisplayName("経路設計者として")
    class AsRoutingPlanner {

        @Test
        @DisplayName("登録すると 201 で内容を返す")
        void registers() throws Exception {
            when(registerVoyage.register(any()))
                    .thenReturn(new VoyageOutcome.Registered(voyage("さくら丸")));

            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.voyageNumber").value("V0100"))
                    .andExpect(jsonPath("$.vesselName").value("さくら丸"))
                    .andExpect(jsonPath("$.originUnLocode").value("JPTYO"))
                    .andExpect(jsonPath("$.destinationUnLocode").value("USLAX"));
        }

        /**
         * 重複は失敗ではなく問いかけである。
         *
         * <p>「登録できません」で終わらせると、経路設計者は別の番号を作る（同じ航海が 2 つになる）。
         */
        @Test
        @DisplayName("同じ航海番号があるときは差分を添えて返す")
        void reportsDifference() throws Exception {
            when(registerVoyage.register(any())).thenReturn(new VoyageOutcome.AlreadyExists(
                    voyage("つばき丸"),
                    new VoyageDifference(List.of(
                            new VoyageDifference.Change("船名", "つばき丸", "さくら丸")))));

            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.hasChanges").value(true))
                    .andExpect(jsonPath("$.existing.vesselName").value("つばき丸"))
                    .andExpect(jsonPath("$.changes[0].item").value("船名"))
                    .andExpect(jsonPath("$.changes[0].before").value("つばき丸"))
                    .andExpect(jsonPath("$.changes[0].after").value("さくら丸"));
        }

        @Test
        @DisplayName("上書きすると 200 で新しい内容を返す")
        void updates() throws Exception {
            when(registerVoyage.overwrite(any()))
                    .thenReturn(new VoyageOutcome.Registered(voyage("さくら丸")));

            mockMvc.perform(put("/api/v1/voyages/V0100")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vesselName").value("さくら丸"));
        }

        @Test
        @DisplayName("存在しない航海番号への上書きは 404（打ち間違いを新規登録にしない）")
        void doesNotCreateOnUpdate() throws Exception {
            when(registerVoyage.overwrite(any())).thenReturn(new VoyageOutcome.NotFound("V0100"));

            mockMvc.perform(put("/api/v1/voyages/V0100")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("URL と入力内容の航海番号が食い違えば 400")
        void rejectsMismatchedVoyageNumber() throws Exception {
            mockMvc.perform(put("/api/v1/voyages/V0999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isBadRequest());

            verify(registerVoyage, never()).overwrite(any());
        }

        @Test
        @DisplayName("登録されていない地点は受け付けない")
        void rejectsUnknownLocation() throws Exception {
            when(locations.findByUnLocode("USLAX")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isBadRequest())
                    // 入力した値は画面に返さない
                    .andExpect(jsonPath("$.message").value("区間の到着地が見つかりません"));

            verify(registerVoyage, never()).register(any());
        }
    }

    @Nested
    @DisplayName("航海 1 件を取り出すとき")
    class Detail {

        /**
         * 更新のたびに全区間を打ち直させない。
         *
         * <p>10 区間ある航海の到着を 1 日ずらすために全部入力し直すのは、打ち直しの過程で
         * 別の項目が変わる事故を招く。既存の内容を読み出して初期値にできるようにする。
         */
        @Test
        @DisplayName("航海番号で内容を取り出せる")
        void returnsVoyage() throws Exception {
            when(searchVoyage.findByNumber(VoyageNumber.of("V0100")))
                    .thenReturn(Optional.of(voyage("さくら丸")));

            mockMvc.perform(get("/api/v1/voyages/V0100")
                            .header(AuthenticatedUser.USER_ID_HEADER, "planner")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vesselName").value("さくら丸"))
                    .andExpect(jsonPath("$.movements.length()").value(1));
        }

        @Test
        @DisplayName("無い航海番号は 404")
        void notFound() throws Exception {
            when(searchVoyage.findByNumber(any())).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/voyages/V9999")
                            .header(AuthenticatedUser.USER_ID_HEADER, "planner")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("経路設計者以外は取り出せない")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/voyages/V0100")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());

            verify(searchVoyage, never()).findByNumber(any());
        }
    }

    @Nested
    @DisplayName("検索")
    class Search {

        @Test
        @DisplayName("出発地・目的地は UN/LOCODE で指定できる")
        void searchesByUnLocode() throws Exception {
            when(searchVoyage.search(any()))
                    .thenReturn(new SearchVoyageUseCase.Result(List.of(voyage("さくら丸")), 1, 50));

            mockMvc.perform(get("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .param("origin", "JPTYO")
                            .param("destination", "USLAX")
                            .param("cargoType", "GENERAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.voyages[0].voyageNumber").value("V0100"))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.truncated").value(false));

            ArgumentCaptor<VoyageSearchCriteria> criteria =
                    ArgumentCaptor.forClass(VoyageSearchCriteria.class);
            verify(searchVoyage).search(criteria.capture());
            assertThat(criteria.getValue().originUnLocode()).isEqualTo("JPTYO");
            assertThat(criteria.getValue().destinationUnLocode()).isEqualTo("USLAX");
            assertThat(criteria.getValue().cargoType()).isEqualTo(CargoType.GENERAL);
        }

        /**
         * 切ったことを黙らない。
         *
         * <p>黙って切ると、経路設計者は「条件に合う航海はこれで全部だ」と読む。
         */
        @Test
        @DisplayName("上限で切ったことを一覧が伝える")
        void reportsTruncation() throws Exception {
            when(searchVoyage.search(any()))
                    .thenReturn(new SearchVoyageUseCase.Result(List.of(voyage("さくら丸")), 120, 50));

            mockMvc.perform(get("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(120))
                    .andExpect(jsonPath("$.limit").value(50))
                    .andExpect(jsonPath("$.truncated").value(true));
        }

        /** 空文字で絞ると、条件に合う航海が 0 件になる。 */
        @Test
        @DisplayName("空の条件は「指定なし」として扱う")
        void treatsBlankAsNoCondition() throws Exception {
            when(searchVoyage.search(any()))
                    .thenReturn(new SearchVoyageUseCase.Result(List.of(), 0, 50));

            mockMvc.perform(get("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .param("origin", "")
                            .param("destination", " "))
                    .andExpect(status().isOk());

            ArgumentCaptor<VoyageSearchCriteria> criteria =
                    ArgumentCaptor.forClass(VoyageSearchCriteria.class);
            verify(searchVoyage).search(criteria.capture());
            assertThat(criteria.getValue().originUnLocode()).isNull();
            assertThat(criteria.getValue().destinationUnLocode()).isNull();
        }

        @Test
        @DisplayName("営業担当者は検索できない")
        void salesCannotSearch() throws Exception {
            mockMvc.perform(get("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());

            verify(searchVoyage, never()).search(any());
        }

        /**
         * 権限の検査を、入力の検査より先に行う。
         *
         * <p>後にすると、権限の無い呼び出しでも本文が不正なら 400 が返る。本人には
         * 「この操作はできない」ではなく「入力を直せ」と伝わり、権限が無いはずの相手に
         * エンドポイントの入力仕様を教えることにもなる。
         */
        @Test
        @DisplayName("本文が不正でも、権限が無ければ 403（入力の誤りを教えない）")
        void checksPermissionBeforeValidation() throws Exception {
            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());

            verify(registerVoyage, never()).register(any());
        }

        @Test
        @DisplayName("権限があって本文が不正なら 400 で理由を返す")
        void reportsInvalidInputForPermittedCaller() throws Exception {
            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"voyageNumber\": \"\", \"vesselName\": \"\","
                                    + " \"carrierName\": \"\", \"supportedCargoTypes\": [],"
                                    + " \"movements\": []}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());

            verify(registerVoyage, never()).register(any());
        }
    }

    @Nested
    @DisplayName("経路設計者でないとき")
    class AsOtherRoles {

        /**
         * 営業担当者には開かない。
         *
         * <p>開くと営業が航海スケジュールと経路確定まで行えてしまい、職掌分離が崩れる。
         */
        @Test
        @DisplayName("営業担当者は登録できない")
        void salesCannotRegister() throws Exception {
            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(registerVoyage, never()).register(any());
        }

        @Test
        @DisplayName("営業担当者は更新できない")
        void salesCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/voyages/V0100")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(registerVoyage, never()).overwrite(any());
        }

        @Test
        @DisplayName("ロールの無い呼び出しは登録できない")
        void withoutRolesCannotRegister() throws Exception {
            mockMvc.perform(post("/api/v1/voyages")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(registerVoyage, never()).register(any());
        }
    }
}
