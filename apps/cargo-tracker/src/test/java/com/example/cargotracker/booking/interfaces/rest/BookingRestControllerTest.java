package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.internal.commandservices.AssignRouteCommandService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingRestController.class)
@WithMockUser
@DisplayName("BookingRestController")
class BookingRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterBookingCommandService registerBookingCommandService;

    @MockitoBean
    private AssignRouteCommandService assignRouteCommandService;

    @MockitoBean
    private FindBookingQueryService findBookingQueryService;

    @Test
    @DisplayName("予約一覧を JSON で取得できる")
    void list() throws Exception {
        Booking booking = anyBooking();
        when(findBookingQueryService.findAll()).thenReturn(List.of(booking));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(booking.getId().toString()))
                .andExpect(jsonPath("$[0].shipperId").value(booking.getShipperId().toString()))
                .andExpect(jsonPath("$[0].cargoType").value("GENERAL_CARGO"));
    }

    @Test
    @DisplayName("予約詳細を JSON で取得できる")
    void detail() throws Exception {
        Booking booking = anyBooking();
        when(findBookingQueryService.execute(booking.getId())).thenReturn(booking);

        mockMvc.perform(get("/api/bookings/" + booking.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()))
                .andExpect(jsonPath("$.originLocation").value("JPTYO"));
    }

    @Test
    @DisplayName("予約登録 API は 201 と Location を返す")
    void register() throws Exception {
        Booking booking = anyBooking();
        when(registerBookingCommandService.execute(any())).thenReturn(booking.getId());
        when(findBookingQueryService.execute(booking.getId())).thenReturn(booking);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "%s",
                                  "cargoType": "GENERAL_CARGO",
                                  "weightKg": 100.0,
                                  "quantity": 1,
                                  "originLocation": "JPTYO",
                                  "destinationLocation": "USNYC",
                                  "requestedPickupDate": "2025-08-01",
                                  "requestedDeliveryDate": "2025-09-01"
                                }
                                """.formatted(booking.getShipperId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/" + booking.getId()))
                .andExpect(jsonPath("$.id").value(booking.getId().toString()));
    }

    @Test
    @DisplayName("存在しない予約は 404 を返す")
    void detailNotFound() throws Exception {
        BookingId bookingId = BookingId.generate();
        when(findBookingQueryService.execute(bookingId))
                .thenThrow(new BookingNotFoundException(bookingId.toString()));

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("予約が見つかりません: " + bookingId));
    }

    @Test
    @DisplayName("存在しない荷主で登録すると 400 を返す")
    void registerBadRequest() throws Exception {
        when(registerBookingCommandService.execute(any()))
                .thenThrow(new ShipperNotFoundException("missing-shipper"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "00000000-0000-0000-0000-000000000000",
                                  "cargoType": "GENERAL_CARGO",
                                  "weightKg": 100.0,
                                  "quantity": 1,
                                  "originLocation": "JPTYO",
                                  "destinationLocation": "USNYC",
                                  "requestedPickupDate": "2025-08-01",
                                  "requestedDeliveryDate": "2025-09-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("荷主が見つかりません: missing-shipper"));
    }

    private Booking anyBooking() {
        BookingId bookingId = BookingId.generate();
        ShipperId shipperId = ShipperId.generate();
        return Booking.register(
                bookingId,
                shipperId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "USNYC", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 9, 1))
        );
    }
}
