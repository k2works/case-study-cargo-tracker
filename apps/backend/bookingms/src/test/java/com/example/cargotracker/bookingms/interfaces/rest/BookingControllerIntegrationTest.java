package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BookingController 統合テスト（US04）。
 *
 * <p>CommandGateway は {@code @MockitoBean} で置き換え、Axon Server への接続を回避する。
 * 実 Axon との連携検証は IT3 以降の E2E で行う。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("BookingController 統合テスト")
class BookingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(commandGateway.sendAndWait(any())).thenReturn(null);
    }

    /** テスト用に荷主を作成して shipper.id を返す。 */
    private Long registerShipper() throws Exception {
        var result = mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "テスト荷主",
                                    "email": "booking-test-%d@example.com",
                                    "phone": "090-0000-0000",
                                    "shipperType": "INDIVIDUAL"
                                }
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings で予約を登録できる（201、PRELIMINARY）")
    void 予約を登録できる() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "GENERAL",
                                        "weightKg": 100,
                                        "quantity": 1,
                                        "productName": "産業機械",
                                        "dimensions": {"lengthCm": 100, "widthCm": 50, "heightCm": 30}
                                    },
                                    "routeSpec": {
                                        "originUnLocode": "JPYOK",
                                        "destinationUnLocode": "USLAX",
                                        "arrivalDeadline": "2099-12-31"
                                    }
                                }
                                """.formatted(shipperId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andExpect(jsonPath("$.bookingStatus").value("PRELIMINARY"));

        // CommandGateway に BookCargoCommand が送信されたことを検証
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BookCargoCommand.class);
        BookCargoCommand cmd = (BookCargoCommand) captor.getValue();
        assertThat(cmd.shipperId().value()).isEqualTo(shipperId);
        assertThat(cmd.cargoSpec().productName()).isEqualTo("産業機械");
        assertThat(cmd.routeSpec().origin().unLocode().value()).isEqualTo("JPYOK");
    }

    @Test
    @DisplayName("存在しない shipperId で 400 を返す")
    void 存在しないShipperIdで400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": 99999,
                                    "cargoSpec": {
                                        "cargoType": "GENERAL",
                                        "weightKg": 100,
                                        "quantity": 1,
                                        "productName": "産業機械",
                                        "dimensions": {"lengthCm": 100, "widthCm": 50, "heightCm": 30}
                                    },
                                    "routeSpec": {
                                        "originUnLocode": "JPYOK",
                                        "destinationUnLocode": "USLAX",
                                        "arrivalDeadline": "2099-12-31"
                                    }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("荷主 ID")));
    }

    @Test
    @DisplayName("origin と destination が同一で 400")
    void 同一originDestinationで400() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "GENERAL",
                                        "weightKg": 100,
                                        "quantity": 1,
                                        "productName": "産業機械",
                                        "dimensions": {"lengthCm": 100, "widthCm": 50, "heightCm": 30}
                                    },
                                    "routeSpec": {
                                        "originUnLocode": "JPYOK",
                                        "destinationUnLocode": "JPYOK",
                                        "arrivalDeadline": "2099-12-31"
                                    }
                                }
                                """.formatted(shipperId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HAZARDOUS で HazardInfo が無いと 400")
    void 危険物で申告なしは400() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "HAZARDOUS",
                                        "weightKg": 50,
                                        "quantity": 1,
                                        "productName": "燃料",
                                        "dimensions": {"lengthCm": 50, "widthCm": 50, "heightCm": 50}
                                    },
                                    "routeSpec": {
                                        "originUnLocode": "JPYOK",
                                        "destinationUnLocode": "USLAX",
                                        "arrivalDeadline": "2099-12-31"
                                    }
                                }
                                """.formatted(shipperId)))
                .andExpect(status().isBadRequest());
    }
}
