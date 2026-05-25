package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.QuotationCommandService;
import com.example.bookingms.application.QuotationQueryService;
import com.example.bookingms.domain.commands.CreateQuotationCommand;
import com.example.bookingms.domain.projections.QuotationSummary;
import com.example.bookingms.interfaces.rest.dto.CreateQuotationRequest;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
import com.example.bookingms.interfaces.rest.dto.QuotationResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationControllerTest {

    @Mock
    private QuotationCommandService commandService;

    @Mock
    private QuotationQueryService queryService;

    @InjectMocks
    private QuotationController controller;

    private CreateQuotationRequest request(String quotationId, List<CreateQuotationRequest.CandidateInput> candidates) {
        return new CreateQuotationRequest(
                quotationId, "S-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30),
                "GENERAL", new BigDecimal("1500.00"), "電子部品", LocalDate.of(2026, 8, 31),
                candidates);
    }

    @Test
    @DisplayName("US01: 見積を作成すると 201 と見積番号が返り、最安候補が概算金額になる")
    void 見積を作成すると201と見積番号が返る() {
        when(commandService.create(any())).thenReturn(CompletableFuture.completedFuture("Q-001"));
        CreateQuotationRequest req = request("Q-001", List.of(
                new CreateQuotationRequest.CandidateInput("JPTYO → DEHAM", 32, new BigDecimal("1450000"), "JPY"),
                new CreateQuotationRequest.CandidateInput("JPTYO → SGSIN → DEHAM", 28, new BigDecimal("1200000"), "JPY")));

        ResponseEntity<Map<String, String>> response = controller.create(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("quotationId", "Q-001");

        ArgumentCaptor<CreateQuotationCommand> captor = ArgumentCaptor.forClass(CreateQuotationCommand.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().estimatedAmount()).isEqualByComparingTo(new BigDecimal("1200000"));
        assertThat(captor.getValue().candidateRoutes()).hasSize(2);
    }

    @Test
    @DisplayName("US01: 見積番号未指定の場合はサーバーで採番される")
    void 見積番号未指定の場合は採番される() {
        when(commandService.create(any())).thenReturn(CompletableFuture.completedFuture("generated"));
        CreateQuotationRequest req = request(null, List.of());

        ResponseEntity<Map<String, String>> response = controller.create(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("quotationId")).isNotBlank();
    }

    @Test
    @DisplayName("US01: 候補なしの見積でも作成でき概算金額は null")
    void 候補なしでも作成でき概算金額はnull() {
        when(commandService.create(any())).thenReturn(CompletableFuture.completedFuture("Q-002"));
        CreateQuotationRequest req = request("Q-002", List.of());

        controller.create(req);

        ArgumentCaptor<CreateQuotationCommand> captor = ArgumentCaptor.forClass(CreateQuotationCommand.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().estimatedAmount()).isNull();
        assertThat(captor.getValue().candidateRoutes()).isEmpty();
    }

    @Test
    @DisplayName("US01: 見積詳細を取得できる")
    void 見積詳細を取得できる() {
        QuotationSummary summary = new QuotationSummary();
        summary.setQuotationId("Q-001");
        summary.setShipperId("S-001");
        summary.setStatus("DRAFT");
        when(queryService.findById("Q-001")).thenReturn(summary);

        ResponseEntity<QuotationResponse> response = controller.findById("Q-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().quotationId()).isEqualTo("Q-001");
    }

    @Test
    @DisplayName("US01: 存在しない見積は 404 が返る")
    void 存在しない見積は404が返る() {
        when(queryService.findById("UNKNOWN")).thenReturn(null);

        ResponseEntity<QuotationResponse> response = controller.findById("UNKNOWN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US01: 見積一覧を PageResponse で取得できる")
    void 見積一覧をPageResponseで取得できる() {
        QuotationSummary summary = new QuotationSummary();
        summary.setQuotationId("Q-001");
        when(queryService.findAll(any())).thenReturn(List.of(summary));
        when(queryService.count()).thenReturn(1L);

        ResponseEntity<PageResponse<QuotationResponse>> response = controller.findAll(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(1L);
    }
}
