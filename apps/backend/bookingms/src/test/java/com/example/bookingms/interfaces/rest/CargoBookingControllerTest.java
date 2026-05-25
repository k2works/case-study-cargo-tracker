package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.CargoCommandService;
import com.example.bookingms.application.CargoQueryService;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.interfaces.rest.dto.BookCargoRequest;
import com.example.bookingms.interfaces.rest.dto.CargoSummaryResponse;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoBookingControllerTest {

    @Mock
    private CargoCommandService commandService;

    @Mock
    private CargoQueryService queryService;

    @InjectMocks
    private CargoBookingController controller;

    private BookCargoRequest validRequest(String bookingId) {
        return new BookCargoRequest(
                bookingId,
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "GENERAL",
                new BigDecimal("1500.00"),
                120,
                80,
                60,
                10,
                "電子部品");
    }

    @Test
    @DisplayName("US04: 予約登録時に bookingId 未指定なら UUID が採番される")
    void bookingId未指定でUUIDが採番される() {
        when(commandService.book(any(BookCargoCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        ResponseEntity<Map<String, String>> response = controller.book(validRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        String bookingId = response.getBody().get("bookingId");
        assertThat(bookingId).isNotBlank();

        ArgumentCaptor<BookCargoCommand> captor = ArgumentCaptor.forClass(BookCargoCommand.class);
        org.mockito.Mockito.verify(commandService).book(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(captor.getValue().shipperId()).isEqualTo("S-001");
    }

    @Test
    @DisplayName("US04: 予約 ID 指定時はそのまま採用される")
    void bookingId指定で同一IDが採用される() {
        when(commandService.book(any(BookCargoCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        ResponseEntity<Map<String, String>> response = controller.book(validRequest("B-XYZ"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("bookingId")).isEqualTo("B-XYZ");
    }

    @Test
    @DisplayName("US04: 予約が存在しない場合 GET /{id} は 404 を返す")
    void 存在しない予約は404() {
        when(queryService.findByBookingId("B-999")).thenReturn(null);

        ResponseEntity<CargoSummaryResponse> response = controller.findByBookingId("B-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US04: GET /api/v1/bookings は予約のページレスポンスを返す")
    void GET一覧で予約のページレスポンスを返す() {
        CargoSummary p = new CargoSummary();
        p.setBookingId("B-100");
        p.setShipperId("S-001");
        p.setOriginUnlocode("JPTYO");
        p.setDestinationUnlocode("USNYC");
        p.setCargoType("GENERAL");
        p.setBookingStatus("PRELIMINARY");
        p.setRoutingStatus("NOT_ROUTED");
        when(queryService.findAll(new PageRequest(0, 20))).thenReturn(List.of(p));
        when(queryService.count()).thenReturn(1L);

        ResponseEntity<PageResponse<CargoSummaryResponse>> response = controller.findAll(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<CargoSummaryResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).bookingId()).isEqualTo("B-100");
        assertThat(body.totalCount()).isEqualTo(1L);
        assertThat(body.page()).isEqualTo(0);
        assertThat(body.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("IT2 ページネーション: page=2, size=10 が QueryService に渡される")
    void ページネーションパラメータがQueryServiceに渡される() {
        when(queryService.findAll(new PageRequest(2, 10))).thenReturn(List.of());
        when(queryService.count()).thenReturn(0L);

        ResponseEntity<PageResponse<CargoSummaryResponse>> response = controller.findAll(2, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().page()).isEqualTo(2);
        assertThat(response.getBody().size()).isEqualTo(10);
        org.mockito.Mockito.verify(queryService).findAll(new PageRequest(2, 10));
    }

    @Test
    @DisplayName("US06: handoff で経路設計引き渡しコマンドが送信され 200 が返る")
    void US06_handoffで経路設計引き渡しコマンドが送信される() {
        when(commandService.requestRouteDesign(any(RequestRouteDesignCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<Void> response = controller.handoff("B-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        org.mockito.Mockito.verify(commandService).requestRouteDesign(any(RequestRouteDesignCommand.class));
    }

    @Test
    @DisplayName("IT2 ページネーション: page=-1, size=0 のサニタイズ")
    void 無効なページパラメータがサニタイズされる() {
        when(queryService.findAll(new PageRequest(0, 20))).thenReturn(List.of());
        when(queryService.count()).thenReturn(0L);

        ResponseEntity<PageResponse<CargoSummaryResponse>> response = controller.findAll(-1, 0);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().page()).isEqualTo(0);
        assertThat(response.getBody().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("US05: 危険物予約リクエストで hazard 情報が Command に渡される")
    void 危険物予約リクエストでhazard情報がCommandに渡される() {
        when(commandService.book(any(BookCargoCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        BookCargoRequest request = new BookCargoRequest(
                null,
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "HAZARDOUS",
                new BigDecimal("1500.00"),
                120, 80, 60,
                10,
                "アセトン",
                "3",
                "UN1090",
                "引火性液体・直射日光厳禁",
                null,
                null);

        ResponseEntity<Map<String, String>> response = controller.book(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<BookCargoCommand> captor = ArgumentCaptor.forClass(BookCargoCommand.class);
        org.mockito.Mockito.verify(commandService).book(captor.capture());
        BookCargoCommand cmd = captor.getValue();
        assertThat(cmd.cargoSpec().cargoType().name()).isEqualTo("HAZARDOUS");
        assertThat(cmd.cargoSpec().hazardInfo()).isNotNull();
        assertThat(cmd.cargoSpec().hazardInfo().imoClass()).isEqualTo("3");
        assertThat(cmd.cargoSpec().hazardInfo().unNumber()).isEqualTo("UN1090");
        assertThat(cmd.cargoSpec().hazardInfo().declaration()).isEqualTo("引火性液体・直射日光厳禁");
        assertThat(cmd.cargoSpec().temperatureCondition()).isNull();
    }

    @Test
    @DisplayName("US05: 冷凍予約リクエストで temperatureCondition が Command に渡される")
    void 冷凍予約リクエストでtemperatureConditionがCommandに渡される() {
        when(commandService.book(any(BookCargoCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        BookCargoRequest request = new BookCargoRequest(
                null,
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "REFRIGERATED",
                new BigDecimal("2000.00"),
                150, 100, 80,
                20,
                "冷凍マグロ",
                null,
                null,
                null,
                new BigDecimal("-25.0"),
                new BigDecimal("-18.0"));

        ResponseEntity<Map<String, String>> response = controller.book(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<BookCargoCommand> captor = ArgumentCaptor.forClass(BookCargoCommand.class);
        org.mockito.Mockito.verify(commandService).book(captor.capture());
        BookCargoCommand cmd = captor.getValue();
        assertThat(cmd.cargoSpec().cargoType().name()).isEqualTo("REFRIGERATED");
        assertThat(cmd.cargoSpec().temperatureCondition()).isNotNull();
        assertThat(cmd.cargoSpec().temperatureCondition().minCelsius()).isEqualByComparingTo("-25.0");
        assertThat(cmd.cargoSpec().temperatureCondition().maxCelsius()).isEqualByComparingTo("-18.0");
        assertThat(cmd.cargoSpec().hazardInfo()).isNull();
    }

    @Test
    @DisplayName("US05: CargoSummaryResponse.from で hazard / temperature を変換できる")
    void CargoSummaryResponseFromでhazardとtemperatureが変換される() {
        CargoSummary p = new CargoSummary();
        p.setBookingId("B-200");
        p.setShipperId("S-001");
        p.setOriginUnlocode("JPTYO");
        p.setDestinationUnlocode("USNYC");
        p.setCargoType("HAZARDOUS");
        p.setBookingStatus("PRELIMINARY");
        p.setRoutingStatus("NOT_ROUTED");
        p.setHazardImoClass("3");
        p.setHazardUnNumber("UN1090");
        p.setHazardDeclaration("引火性液体");
        p.setTemperatureMinC(new BigDecimal("-25.0"));
        p.setTemperatureMaxC(new BigDecimal("-18.0"));
        when(queryService.findByBookingId("B-200")).thenReturn(p);

        ResponseEntity<CargoSummaryResponse> response = controller.findByBookingId("B-200");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CargoSummaryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.hazardImoClass()).isEqualTo("3");
        assertThat(body.hazardUnNumber()).isEqualTo("UN1090");
        assertThat(body.hazardDeclaration()).isEqualTo("引火性液体");
        assertThat(body.temperatureMinC()).isEqualByComparingTo("-25.0");
        assertThat(body.temperatureMaxC()).isEqualByComparingTo("-18.0");
    }
}
