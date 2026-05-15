package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
// 各 @Test は cargoType / hazardInfo / temperatureCondition の組合せをドキュメント的に
// 示すため、@DisplayName 付きで個別ケースに分ける方針を採用（パラメータ化テストに統合
// すると失敗時の追跡性と意図の伝達性が下がる）。
@SuppressWarnings("java:S5976")
class BookingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommandGateway commandGateway;

    @Autowired
    private CargoSummaryMapper cargoSummaryMapper;

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

    @Test
    @DisplayName("US05: HAZARDOUS で HazardInfo を含めて登録できる（201）")
    void 危険物予約を登録できる() throws Exception {
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
                                        "dimensions": {"lengthCm": 50, "widthCm": 50, "heightCm": 50},
                                        "hazardInfo": {
                                            "imoClass": "3",
                                            "unNumber": "1170",
                                            "declaration": "引火性液体"
                                        }
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        BookCargoCommand cmd = (BookCargoCommand) captor.getValue();
        assertThat(cmd.cargoSpec().cargoType().name()).isEqualTo("HAZARDOUS");
        assertThat(cmd.cargoSpec().hazardInfo()).isNotNull();
        assertThat(cmd.cargoSpec().hazardInfo().imoClass()).isEqualTo("3");
        assertThat(cmd.cargoSpec().hazardInfo().unNumber()).isEqualTo("1170");
    }

    @Test
    @DisplayName("US05: REFRIGERATED で TemperatureCondition を含めて登録できる（201）")
    void 冷凍貨物予約を登録できる() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "REFRIGERATED",
                                        "weightKg": 200,
                                        "quantity": 1,
                                        "productName": "冷凍マグロ",
                                        "dimensions": {"lengthCm": 150, "widthCm": 80, "heightCm": 80},
                                        "temperatureCondition": {
                                            "minCelsius": -20,
                                            "maxCelsius": -10
                                        }
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).sendAndWait(captor.capture());
        BookCargoCommand cmd = (BookCargoCommand) captor.getValue();
        assertThat(cmd.cargoSpec().cargoType().name()).isEqualTo("REFRIGERATED");
        assertThat(cmd.cargoSpec().temperatureCondition()).isNotNull();
        assertThat(cmd.cargoSpec().temperatureCondition().minCelsius()).isEqualByComparingTo("-20");
        assertThat(cmd.cargoSpec().temperatureCondition().maxCelsius()).isEqualByComparingTo("-10");
    }

    @Test
    @DisplayName("US05: REFRIGERATED で TemperatureCondition が無いと 400")
    void 冷凍貨物で温度なしは400() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "REFRIGERATED",
                                        "weightKg": 200,
                                        "quantity": 1,
                                        "productName": "冷凍マグロ",
                                        "dimensions": {"lengthCm": 150, "widthCm": 80, "heightCm": 80}
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

    @Test
    @DisplayName("GET /api/v1/bookings で予約一覧を取得できる（200、cargo_summary を投影）")
    void 予約一覧を取得できる() throws Exception {
        Long shipperId = registerShipper();

        // Read Model に直接 INSERT（CommandGateway はモックのため Projection は走らない）
        CargoSummaryRecord summary = new CargoSummaryRecord();
        summary.setBookingId("test-booking-list-1");
        summary.setShipperId(shipperId);
        summary.setOriginUnlocode("JPYOK");
        summary.setDestinationUnlocode("USLAX");
        summary.setArrivalDeadline(LocalDate.of(2099, 12, 31));
        summary.setCargoType("GENERAL");
        summary.setWeightKg(BigDecimal.valueOf(100));
        summary.setLengthCm(100);
        summary.setWidthCm(50);
        summary.setHeightCm(30);
        summary.setQuantity(1);
        summary.setProductName("テスト貨物 A");
        summary.setBookingStatus("PRELIMINARY");
        summary.setRoutingStatus("NOT_ROUTED");
        cargoSummaryMapper.insert(summary);

        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')]").exists())
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')].productName")
                        .value("テスト貨物 A"))
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')].originUnLocode")
                        .value("JPYOK"))
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')].destinationUnLocode")
                        .value("USLAX"))
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')].cargoType")
                        .value("GENERAL"))
                .andExpect(jsonPath("$[?(@.bookingId == 'test-booking-list-1')].bookingStatus")
                        .value("PRELIMINARY"));
    }

    @Test
    @DisplayName("US05: REFRIGERATED で min > max は 400（温度範囲整合性）")
    void 温度範囲逆転は400() throws Exception {
        Long shipperId = registerShipper();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shipperId": %d,
                                    "cargoSpec": {
                                        "cargoType": "REFRIGERATED",
                                        "weightKg": 200,
                                        "quantity": 1,
                                        "productName": "冷凍マグロ",
                                        "dimensions": {"lengthCm": 150, "widthCm": 80, "heightCm": 80},
                                        "temperatureCondition": {
                                            "minCelsius": 10,
                                            "maxCelsius": -10
                                        }
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
