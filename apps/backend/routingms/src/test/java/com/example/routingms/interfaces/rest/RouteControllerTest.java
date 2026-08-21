package com.example.routingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.routingms.application.internal.FindRouteCandidatesUseCase;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.TransitEdge;
import com.example.routingms.domain.model.TransitPath;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RouteController.class)
@DisplayName("経路候補算出の API")
class RouteControllerTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindRouteCandidatesUseCase findRouteCandidates;

    private static TransitEdge edge(String voyage, Location from, Location to,
            String departure, String arrival) {
        return TransitEdge.of(VoyageNumber.of(voyage), from, to,
                Instant.parse(departure), Instant.parse(arrival));
    }

    private static TransitPath direct() {
        return TransitPath.of(List.of(
                edge("V-DIRECT", TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", "2026-09-15T12:00:00Z")));
    }

    private static TransitPath viaShanghai() {
        return TransitPath.of(List.of(
                edge("V-A", TOKYO, SHANGHAI, "2026-09-01T09:00:00Z", "2026-09-03T09:00:00Z"),
                edge("V-B", SHANGHAI, LOS_ANGELES, "2026-09-04T09:00:00Z", "2026-09-18T09:00:00Z")));
    }

    private static RouteSearchSpecification specification() {
        return RouteSearchSpecification.of(TOKYO, LOS_ANGELES,
                Instant.parse("2026-09-30T14:59:59Z"), CargoType.GENERAL);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return get("/api/v1/routes")
                .param("origin", "JPTYO")
                .param("destination", "USLAX")
                .param("deadline", "2026-09-30")
                .param("cargoType", "GENERAL");
    }

    @Nested
    @DisplayName("経路設計者として")
    class AsRoutingPlanner {

        @Test
        @DisplayName("候補を推奨順で返す")
        void returnsRankedCandidates() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenReturn(new FindRouteCandidatesUseCase.Result(
                            List.of(direct(), viaShanghai()), specification()));

            mockMvc.perform(request()
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.candidates[0].rank").value(1))
                    .andExpect(jsonPath("$.candidates[0].direct").value(true))
                    .andExpect(jsonPath("$.candidates[1].rank").value(2));
        }

        /** 受入基準が求める項目をすべて返す。 */
        @Test
        @DisplayName("候補ごとに所要日数・経由港・費用・航海番号を返す")
        void returnsEveryFieldTheScreenNeeds() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenReturn(new FindRouteCandidatesUseCase.Result(
                            List.of(viaShanghai()), specification()));

            mockMvc.perform(request()
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidates[0].transitDays").value(17))
                    .andExpect(jsonPath("$.candidates[0].transshipmentCount").value(1))
                    .andExpect(jsonPath("$.candidates[0].transitPorts[0].unLocode").value("CNSHA"))
                    // 画面に UN/LOCODE の対訳表を持たせない
                    .andExpect(jsonPath("$.candidates[0].transitPorts[0].name").value("Shanghai"))
                    .andExpect(jsonPath("$.candidates[0].voyageNumbers[0]").value("V-A"))
                    .andExpect(jsonPath("$.candidates[0].estimatedCost").isNumber())
                    .andExpect(jsonPath("$.candidates[0].legs[0].fromName").value("Tokyo"));
        }

        /**
         * 「無い」は正常な結果である。
         *
         * <p>404 にすると画面は「エラーが起きた」と伝えることになる。経路設計者に必要なのは
         * 「この条件では見つからなかった」であり、そこから条件を緩める操作へ進めることである。
         */
        @Test
        @DisplayName("候補が 0 件でも 200 で、使った条件を返す")
        void returnsOkWithCriteriaWhenEmpty() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenReturn(new FindRouteCandidatesUseCase.Result(List.of(), specification()));

            mockMvc.perform(request()
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.candidates").isEmpty())
                    .andExpect(jsonPath("$.appliedCriteria.maxTransshipments").value(2))
                    .andExpect(jsonPath("$.appliedCriteria.originName").value("Tokyo"));
        }

        /**
         * 画面が送る型と、サーバが受け取る型を突き合わせる（IT3 Try 4）。
         *
         * <p>IT3 では画面が日付を送り、サーバが日時で受け取っていた。モックが文字列で
         * 前方比較して「たまたま動く」ため、単体も E2E も緑のまま実バックエンドで落ちた。
         */
        @Test
        @DisplayName("期限は日付（YYYY-MM-DD）で受け取り、そのままユースケースへ渡す")
        void passesTheDeadlineAsADate() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenReturn(new FindRouteCandidatesUseCase.Result(List.of(), specification()));

            mockMvc.perform(request()
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk());

            verify(findRouteCandidates).find("JPTYO", "USLAX",
                    LocalDate.of(2026, Month.SEPTEMBER, 30), CargoType.GENERAL, null);
        }

        @Test
        @DisplayName("積み替えの上限を指定できる（条件を緩めた再算出）")
        void acceptsALooserTransshipmentLimit() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenReturn(new FindRouteCandidatesUseCase.Result(List.of(), specification()));

            mockMvc.perform(request().param("maxTransshipments", "3")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isOk());

            verify(findRouteCandidates).find(any(), any(), any(), any(), eq(3));
        }

        @Test
        @DisplayName("港の指定が誤っていれば、経路が無いのではなく 400 で理由を返す")
        void reportsUnknownPortAsInvalidInput() throws Exception {
            when(findRouteCandidates.find(any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("出発地が見つかりません: XXXXX"));

            mockMvc.perform(request().param("origin", "XXXXX")
                            .header(AuthenticatedUser.USER_ID_HEADER, "routing01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                    .andExpect(status().isBadRequest())
                    // 入力値そのものは応答に載せない（IT2 で決めた。画面は自分が送った値を知っている）
                    .andExpect(jsonPath("$.message").value("出発地が見つかりません"));
        }
    }

    @Nested
    @DisplayName("経路設計者以外として")
    class AsOthers {

        @Test
        @DisplayName("403 で拒否し、ユースケースを呼ばない")
        void rejectsOtherRoles() throws Exception {
            mockMvc.perform(request()
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());

            verify(findRouteCandidates, never()).find(any(), any(), any(), any(), any());
        }

        /**
         * 認可は入力の検査より先（[ADR-016]）。
         *
         * <p>後にすると、権限の無い相手に「どの項目が必要か」を教えることになる。
         */
        @Test
        @DisplayName("条件が不正でも、権限が無ければ 403（入力の誤りを教えない）")
        void checksPermissionBeforeValidation() throws Exception {
            mockMvc.perform(get("/api/v1/routes")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isForbidden());

            verify(findRouteCandidates, never()).find(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("ロールのヘッダが無い呼び出しも 403")
        void rejectsMissingRoleHeader() throws Exception {
            mockMvc.perform(request().header(AuthenticatedUser.USER_ID_HEADER, "someone"))
                    .andExpect(status().isForbidden());
        }
    }
}
