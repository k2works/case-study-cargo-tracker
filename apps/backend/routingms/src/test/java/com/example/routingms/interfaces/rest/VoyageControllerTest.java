package com.example.routingms.interfaces.rest;

import com.example.routingms.application.VoyageCommandService;
import com.example.routingms.application.VoyageQueryService;
import com.example.routingms.application.VoyageSearchCriteria;
import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.UpdateVoyageScheduleCommand;
import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.interfaces.rest.dto.RegisterVoyageRequest;
import com.example.routingms.interfaces.rest.dto.UpdateVoyageScheduleRequest;
import com.example.routingms.interfaces.rest.dto.VoyageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoyageControllerTest {

    @Mock
    private VoyageCommandService commandService;

    @Mock
    private VoyageQueryService queryService;

    @InjectMocks
    private VoyageController controller;

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 6, 10, 18, 0);

    private VoyageProjection makeProjection(String voyageNumber) {
        VoyageProjection p = new VoyageProjection();
        p.setVoyageNumber(voyageNumber);
        p.setCarrierCode("CARRIER-A");
        p.setCarrierName("運送会社A");
        p.setShipName("SHIPα");
        p.setOriginUnlocode("JPTYO");
        p.setDestUnlocode("USNYC");
        p.setDepartureDate(DEPARTURE);
        p.setArrivalDate(ARRIVAL);
        p.setStatus("ACTIVE");
        p.setAcceptedCargoTypes(List.of("GENERAL"));
        return p;
    }

    @Test
    void 航海を新規登録すると201が返る() {
        when(commandService.register(any(RegisterVoyageCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("V001"));

        var request = new RegisterVoyageRequest(
                "V001", "CARRIER-A", "運送会社A", "SHIPα",
                "JPTYO", "USNYC", DEPARTURE, ARRIVAL, List.of(), List.of("GENERAL"));

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(commandService).register(any(RegisterVoyageCommand.class));
    }

    @Test
    void 航海を更新すると200が返る() {
        when(commandService.update(any(UpdateVoyageScheduleCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var request = new UpdateVoyageScheduleRequest(DEPARTURE, ARRIVAL, null, List.of("GENERAL"));

        ResponseEntity<Void> response = controller.update("V001", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(commandService).update(any(UpdateVoyageScheduleCommand.class));
    }

    @Test
    void 全航海一覧を取得できる() {
        when(queryService.findAll()).thenReturn(List.of(makeProjection("V001"), makeProjection("V002")));

        ResponseEntity<List<VoyageResponse>> response = controller.findAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void 航海番号で航海を取得できる() {
        when(queryService.findByVoyageNumber("V001")).thenReturn(makeProjection("V001"));

        ResponseEntity<VoyageResponse> response = controller.findByVoyageNumber("V001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().voyageNumber()).isEqualTo("V001");
    }

    @Test
    void 存在しない航海番号は404が返る() {
        when(queryService.findByVoyageNumber("UNKNOWN")).thenReturn(null);

        ResponseEntity<VoyageResponse> response = controller.findByVoyageNumber("UNKNOWN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 条件を指定して航海を検索できる() {
        when(queryService.search(any(VoyageSearchCriteria.class)))
                .thenReturn(List.of(makeProjection("V001")));

        ResponseEntity<List<VoyageResponse>> response =
                controller.search("JPTYO", "USNYC", DEPARTURE, ARRIVAL, "GENERAL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).voyageNumber()).isEqualTo("V001");
        verify(queryService).search(any(VoyageSearchCriteria.class));
    }

    @Test
    void 検索条件なしでも全件検索として扱われる() {
        when(queryService.search(any(VoyageSearchCriteria.class)))
                .thenReturn(List.of(makeProjection("V001"), makeProjection("V002")));

        ResponseEntity<List<VoyageResponse>> response =
                controller.search(null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void 登録リクエストでmovementsがnullの場合も正常処理される() {
        when(commandService.register(any())).thenReturn(CompletableFuture.completedFuture("V001"));

        var request = new RegisterVoyageRequest(
                "V001", "CARRIER-A", "運送会社A", "SHIPα",
                "JPTYO", "USNYC", DEPARTURE, ARRIVAL, null, null);

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
