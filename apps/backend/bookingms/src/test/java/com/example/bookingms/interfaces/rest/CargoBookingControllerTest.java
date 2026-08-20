package com.example.bookingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CargoBookingController.class)
@DisplayName("貨物予約 API")
class CargoBookingControllerTest {

    private static final String BODY = """
            {"shipperId": 1, "type": "GENERAL", "weightKg": 12000, "quantity": 20,
             "description": "電子部品", "lengthCm": 120, "widthCm": 80, "heightCm": 100,
             "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
             "departureDate": "2027-09-01", "arrivalDeadline": "2027-09-20"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookCargoUseCase bookCargo;

    @MockitoBean
    private SearchCargoUseCase searchCargo;

    @MockitoBean
    private LocationRepository locations;

    private static Cargo booked() {
        return Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L, BookingStatus.PRELIMINARY,
                TransportStatus.NOT_RECEIVED, RoutingStatus.NOT_ROUTED,
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(Location.of("JPTYO", "Tokyo"),
                        Location.of("USLAX", "Los Angeles"), LocalDate.of(2027, 9, 1),
                        LocalDate.of(2027, 9, 20)));
    }

    @Nested
    @DisplayName("営業担当者として")
    class AsSales {

        @Test
        @DisplayName("予約を登録すると 201・予約番号・仮受付を返す")
        void books() throws Exception {
            when(bookCargo.book(any())).thenReturn(booked());

            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                    .andExpect(jsonPath("$.bookingStatus").value("PRELIMINARY"))
                    // 「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）
                    .andExpect(jsonPath("$.transportStatus").value("NOT_RECEIVED"))
                    .andExpect(jsonPath("$.routingStatus").value("NOT_ROUTED"))
                    // 地点は名称まで返す。画面がコードから名称を引き直さずに済む
                    .andExpect(jsonPath("$.originName").value("Tokyo"))
                    .andExpect(jsonPath("$.destinationName").value("Los Angeles"));
        }

        @Test
        @DisplayName("入力の誤りは理由を添えて 400 で返す")
        void reportsInvalidInput() throws Exception {
            when(bookCargo.book(any()))
                    .thenThrow(new IllegalArgumentException("指定された荷主が見つかりません: 999"));

            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.replace("\"shipperId\": 1", "\"shipperId\": 999")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("指定された荷主が見つかりません: 999"));
        }

        @Test
        @DisplayName("一覧は総件数と上限を添えて返す")
        void searches() throws Exception {
            when(searchCargo.search(null, null))
                    .thenReturn(new SearchCargoUseCase.Result(List.of(booked()), 1L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookings[0].bookingId").value("BKG-2026000001"))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.limit").value(100))
                    // 上限で切ったことを黙っていると「全件見た」と受け取られる
                    .andExpect(jsonPath("$.truncated").value(false));
        }

        @Test
        @DisplayName("種別で絞り込める")
        void filtersByType() throws Exception {
            when(searchCargo.search(CargoType.HAZARDOUS, null))
                    .thenReturn(new SearchCargoUseCase.Result(List.of(), 0L, 100));

            mockMvc.perform(get("/api/v1/bookings")
                            .param("type", "HAZARDOUS")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk());

            verify(searchCargo).search(CargoType.HAZARDOUS, null);
        }

        @Test
        @DisplayName("地点の選択肢を返す")
        void listsLocations() throws Exception {
            when(locations.findAll()).thenReturn(List.of(Location.of("JPTYO", "Tokyo")));

            mockMvc.perform(get("/api/v1/bookings/locations")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].unLocode").value("JPTYO"))
                    .andExpect(jsonPath("$[0].name").value("Tokyo"));
        }
    }

    @Nested
    @DisplayName("営業担当者以外として")
    class AsOthers {

        /**
         * 荷主ロールにも開かない（ADR-008）。
         *
         * <p>利用者と荷主を結ぶキーが無く「自分の予約だけ」に絞り込めないため、開くと
         * 全荷主の予約が見える。「まだ作っていない」を「開いていない」と取り違えないよう、
         * 荷主ロールが 403 を受けることを明示的に確かめる。
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "ROLE_SHIPPER", "ROLE_ROUTING", "ROLE_HANDLER", "ROLE_TRACKER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"
        })
        @DisplayName("登録は 403 で拒否し、ユースケースを呼ばない")
        void rejectsBooking(String role) throws Exception {
            mockMvc.perform(post("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());

            verify(bookCargo, never()).book(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "ROLE_SHIPPER", "ROLE_ROUTING", "ROLE_HANDLER", "ROLE_TRACKER",
            "ROLE_ACCOUNTANT", "ROLE_ADMIN"
        })
        @DisplayName("参照も 403 で拒否する")
        void rejectsSearch(String role) throws Exception {
            mockMvc.perform(get("/api/v1/bookings")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isForbidden());

            verify(searchCargo, never()).search(any(), any());
        }

        @Test
        @DisplayName("クレームが無ければ 400（Gateway を通っていない呼び出し）")
        void rejectsRequestWithoutClaims() throws Exception {
            mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isBadRequest());
        }
    }
}
