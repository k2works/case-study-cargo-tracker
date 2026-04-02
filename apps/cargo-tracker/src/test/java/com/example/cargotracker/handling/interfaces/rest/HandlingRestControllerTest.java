package com.example.cargotracker.handling.interfaces.rest;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.DuplicateReceiveException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
import com.example.cargotracker.handling.application.internal.queryservices.FindHandlingEventsQueryService;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HandlingRestController.class)
@WithMockUser
@DisplayName("HandlingRestController")
class HandlingRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordHandlingEventCommandService recordHandlingEventCommandService;

    @MockitoBean
    private FindHandlingEventsQueryService findHandlingEventsQueryService;

    @Test
    @DisplayName("荷役イベントを記録すると 201 と Location ヘッダーが返る")
    void record_returns201() throws Exception {
        HandlingEventId generatedId = HandlingEventId.generate();
        when(recordHandlingEventCommandService.execute(any())).thenReturn(generatedId);

        String requestBody = """
                {
                  "bookingId": "%s",
                  "eventType": "LOAD",
                  "locationCode": "JPTYO",
                  "completionTime": "2025-01-15T10:00:00",
                  "memo": "テスト記録"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/handling-events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/handling-events/")))
                .andExpect(jsonPath("$.eventType").value("LOAD"))
                .andExpect(jsonPath("$.locationCode").value("JPTYO"));
    }

    @Test
    @DisplayName("必須フィールド欠如は 400 を返す")
    void record_missingFields_returns400() throws Exception {
        String requestBody = """
                {
                  "eventType": "LOAD"
                }
                """;

        mockMvc.perform(post("/api/v1/handling-events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("存在しない予約 ID は 404 を返す")
    void record_bookingNotFound_returns404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(recordHandlingEventCommandService.execute(any()))
                .thenThrow(new BookingNotFoundException(bookingId.toString()));

        String requestBody = """
                {
                  "bookingId": "%s",
                  "eventType": "LOAD",
                  "locationCode": "JPTYO",
                  "completionTime": "2025-01-15T10:00:00"
                }
                """.formatted(bookingId);

        mockMvc.perform(post("/api/v1/handling-events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RECEIVE イベントの重複登録は 409 Conflict を返す")
    void record_duplicateReceive_returns409() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(recordHandlingEventCommandService.execute(any()))
                .thenThrow(new DuplicateReceiveException(bookingId));

        String requestBody = """
                {
                  "bookingId": "%s",
                  "eventType": "RECEIVE",
                  "locationCode": "JPTYO",
                  "completionTime": "2025-01-15T10:00:00"
                }
                """.formatted(bookingId);

        mockMvc.perform(post("/api/v1/handling-events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("bookingId で荷役イベント一覧を取得できる")
    void listByBookingId_returnsEvents() throws Exception {
        UUID bookingId = UUID.randomUUID();
        HandlingEvent event = anyHandlingEvent(bookingId);
        when(findHandlingEventsQueryService.findByBookingId(bookingId)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/handling-events")
                        .param("bookingId", bookingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$[0].eventType").value("LOAD"))
                .andExpect(jsonPath("$[0].locationCode").value("JPTYO"));
    }

    // ── ヘルパー ──────────────────────────────────────────────────────────

    private HandlingEvent anyHandlingEvent(UUID bookingId) {
        return HandlingEvent.reconstitute(
                HandlingEventId.generate(),
                bookingId,
                HandlingEventType.LOAD,
                "JPTYO",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                null
        );
    }
}
