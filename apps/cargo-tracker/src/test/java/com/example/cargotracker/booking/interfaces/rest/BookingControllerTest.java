package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.internal.commandservices.RegisterBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ShipperNotFoundException;
import com.example.cargotracker.booking.application.internal.queryservices.BookingNotFoundException;
import com.example.cargotracker.booking.application.internal.queryservices.FindBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@WithMockUser
@DisplayName("BookingController")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterBookingCommandService registerBookingCommandService;

    @MockitoBean
    private FindBookingQueryService findBookingQueryService;

    @MockitoBean
    private ShipperExistencePort shipperExistencePort;

    // ── GET /bookings/new ──────────────────────────────────────────────────

    @Test
    @DisplayName("予約一覧を表示できる")
    void showList() throws Exception {
        when(findBookingQueryService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/list"))
                .andExpect(model().attributeExists("bookings"));
    }

    @Test
    @DisplayName("予約登録フォームを表示できる")
    void showRegisterForm() throws Exception {
        mockMvc.perform(get("/bookings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/register"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attributeExists("cargoTypes"));
    }

    // ── POST /bookings ─────────────────────────────────────────────────────

    @Test
    @DisplayName("バリデーションエラーがある場合は登録フォームに戻る")
    void returnRegisterFormOnValidationError() throws Exception {
        mockMvc.perform(post("/bookings")
                        .param("shipperId", "")         // 必須項目が空
                        .param("quantity", "1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/register"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @DisplayName("荷主が存在しない場合はエラーメッセージをセットして登録フォームに戻る")
    void returnRegisterFormWhenShipperNotFound() throws Exception {
        when(registerBookingCommandService.execute(any()))
                .thenThrow(new ShipperNotFoundException("荷主が見つかりません"));

        mockMvc.perform(post("/bookings")
                        .param("shipperId", UUID.randomUUID().toString())
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "100.0")
                        .param("quantity", "1")
                        .param("originLocation", "JPTYO")
                        .param("destinationLocation", "USNYC")
                        .param("requestedPickupDate", "2025-08-01")
                        .param("requestedDeliveryDate", "2025-09-01")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/register"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("登録成功時は予約詳細へリダイレクトする")
    void redirectToDetailOnSuccess() throws Exception {
        BookingId bookingId = BookingId.generate();
        when(registerBookingCommandService.execute(any())).thenReturn(bookingId);

        mockMvc.perform(post("/bookings")
                        .param("shipperId", UUID.randomUUID().toString())
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "100.0")
                        .param("quantity", "1")
                        .param("originLocation", "JPTYO")
                        .param("destinationLocation", "USNYC")
                        .param("requestedPickupDate", "2025-08-01")
                        .param("requestedDeliveryDate", "2025-09-01")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings/" + bookingId));
    }

    // ── GET /bookings/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("予約詳細を表示できる")
    void showDetail() throws Exception {
        BookingId bookingId = BookingId.generate();
        ShipperId shipperId = ShipperId.generate();
        Booking booking = Booking.register(
                bookingId, shipperId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "USNYC", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 9, 1))
        );
        when(findBookingQueryService.execute(bookingId)).thenReturn(booking);

        mockMvc.perform(get("/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/detail"))
                .andExpect(model().attributeExists("booking"));
    }

    @Test
    @DisplayName("存在しない予約 ID を指定した場合は 404 を返す")
    void returnNotFoundWhenBookingNotFound() throws Exception {
        BookingId bookingId = BookingId.generate();
        when(findBookingQueryService.execute(bookingId))
                .thenThrow(new BookingNotFoundException(bookingId.value().toString()));

        mockMvc.perform(get("/bookings/" + bookingId))
                .andExpect(status().isNotFound());
    }

    // ── POST /bookings/lookup-shipper ───────────────────────────────────────

    @Test
    @DisplayName("荷主名が見つかった場合はフラグメントに名前をセットする")
    void lookupShipperFound() throws Exception {
        UUID shipperId = UUID.randomUUID();
        when(shipperExistencePort.findNameById(shipperId)).thenReturn(java.util.Optional.of("山田太郎"));

        mockMvc.perform(post("/bookings/lookup-shipper")
                        .param("shipperId", shipperId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/fragments/shipper-name"))
                .andExpect(model().attribute("shipperName", "山田太郎"));
    }

    @Test
    @DisplayName("荷主が見つからない場合はフラグメントに「見つかりません」をセットする")
    void lookupShipperNotFound() throws Exception {
        UUID shipperId = UUID.randomUUID();
        when(shipperExistencePort.findNameById(shipperId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/bookings/lookup-shipper")
                        .param("shipperId", shipperId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/fragments/shipper-name"))
                .andExpect(model().attribute("shipperName", "（荷主が見つかりません）"));
    }

    @Test
    @DisplayName("無効な UUID の場合はフラグメントにエラーメッセージをセットする")
    void lookupShipperInvalidId() throws Exception {
        mockMvc.perform(post("/bookings/lookup-shipper")
                        .param("shipperId", "invalid-uuid")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/fragments/shipper-name"))
                .andExpect(model().attribute("shipperName", "（無効な荷主 ID です）"));
    }
}
