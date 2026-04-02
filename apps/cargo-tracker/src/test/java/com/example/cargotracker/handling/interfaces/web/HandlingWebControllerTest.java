package com.example.cargotracker.handling.interfaces.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HandlingWebController.class)
@WithMockUser
@DisplayName("HandlingWebController")
class HandlingWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordHandlingEventCommandService recordHandlingEventCommandService;

    @MockitoBean
    private FindHandlingEventsQueryService findHandlingEventsQueryService;

    // ── GET /handling ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /handling は荷役作業一覧を表示する")
    void list_returnsListView() throws Exception {
        when(findHandlingEventsQueryService.findFiltered(any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/handling"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/list"))
                .andExpect(model().attributeExists("handlingEvents", "eventTypes"));
    }

    @Test
    @DisplayName("GET /handling はフィルタ付きで荷役イベントを返す")
    void list_withFilter_returnsFilteredEvents() throws Exception {
        UUID bookingId = UUID.randomUUID();
        HandlingEvent event = anyHandlingEvent(bookingId);
        when(findHandlingEventsQueryService.findFiltered(any(), any(), any()))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/handling")
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/list"))
                .andExpect(model().attributeExists("handlingEvents"));
    }

    // ── GET /handling/new ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /handling/new は荷役作業記録フォームを表示する")
    void showForm_returnsNewView() throws Exception {
        mockMvc.perform(get("/handling/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeExists("form", "eventTypes"))
                .andExpect(model().attribute("eventTypes", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(model().attribute("eventTypes", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(HandlingEventType.RECEIVE))));
    }

    @Test
    @DisplayName("GET /handling/new?bookingId= は bookingId を事前入力する")
    void showForm_withBookingId_preselectsBookingId() throws Exception {
        String bookingId = UUID.randomUUID().toString();

        mockMvc.perform(get("/handling/new").param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attribute("form",
                        org.hamcrest.Matchers.hasProperty("bookingId", org.hamcrest.Matchers.is(bookingId))));
    }

    // ── POST /handling ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /handling は正常に記録して一覧へリダイレクトする")
    void record_success_redirectsToList() throws Exception {
        when(recordHandlingEventCommandService.execute(any()))
                .thenReturn(HandlingEventId.generate());
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/handling?bookingId=" + bookingId))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /handling で対象外イベント種別はフォームエラーを返す")
    void record_nonHandlingOperationType_returnsFormError() throws Exception {
        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeHasFieldErrors("form", "eventType"));
    }

    @Test
    @DisplayName("POST /handling でバリデーションエラーはフォームを再表示する")
    void record_validationError_returnsForm() throws Exception {
        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", "")
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeHasFieldErrors("form", "bookingId"));
    }

    @Test
    @DisplayName("POST /handling で予約が見つからない場合はエラーメッセージを表示する")
    void record_bookingNotFound_showsError() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(recordHandlingEventCommandService.execute(any()))
                .thenThrow(new BookingNotFoundException(bookingId.toString()));

        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /handling で無効な UUID 形式の bookingId はバリデーションエラーを返す")
    void record_invalidUuidBookingId_showsFieldError() throws Exception {
        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", "not-a-valid-uuid")
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeHasFieldErrors("form", "bookingId"));
    }

    // ── GET /handling/receive ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /handling/receive は引取フォームを表示する")
    void showReceiveForm_returnsReceiveView() throws Exception {
        mockMvc.perform(get("/handling/receive"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("GET /handling/receive?bookingId= は bookingId を事前入力する")
    void showReceiveForm_withBookingId_preselectsBookingId() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        mockMvc.perform(get("/handling/receive").param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attribute("form",
                        org.hamcrest.Matchers.hasProperty("bookingId", org.hamcrest.Matchers.is(bookingId))));
    }

    // ── POST /handling/receive ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /handling/receive は正常に引取を記録して一覧へリダイレクトする")
    void createReceive_success_redirectsToList() throws Exception {
        when(recordHandlingEventCommandService.execute(any()))
                .thenReturn(HandlingEventId.generate());
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/handling/receive")
                        .with(csrf())
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00")
                        .param("receiveConfirmationCode", "RC-TEST-001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/handling?bookingId=" + bookingId))
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("精算処理を開始できます")));
    }

    @Test
    @DisplayName("POST /handling/receive で RECEIVE が重複する場合はフィールドエラーを返す")
    void createReceive_duplicateReceive_showsFieldError() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(recordHandlingEventCommandService.execute(any()))
                .thenThrow(new DuplicateReceiveException(bookingId));

        mockMvc.perform(post("/handling/receive")
                        .with(csrf())
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00")
                        .param("receiveConfirmationCode", "RC-TEST-001"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attributeHasFieldErrors("form", "bookingId"));
    }

    @Test
    @DisplayName("POST /handling/receive で確認コードが空の場合はフィールドエラーを返す")
    void createReceive_emptyConfirmationCode_showsFieldError() throws Exception {
        mockMvc.perform(post("/handling/receive")
                        .with(csrf())
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00")
                        .param("receiveConfirmationCode", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attributeHasFieldErrors("form", "receiveConfirmationCode"));
    }

    // ── GET /handling/manual-update ───────────────────────────────────────

    @Test
    @DisplayName("GET /handling/manual-update は手動更新フォームを表示する")
    void showManualUpdateForm_returnsManualUpdateView() throws Exception {
        mockMvc.perform(get("/handling/manual-update"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/manual-update"))
                .andExpect(model().attributeExists("form"));
    }

    // ── POST /handling/manual-update ──────────────────────────────────────

    @Test
    @DisplayName("POST /handling/manual-update はメモ付きで正常に記録して一覧へリダイレクトする")
    void createManualUpdate_withMemo_success() throws Exception {
        when(recordHandlingEventCommandService.execute(any()))
                .thenReturn(HandlingEventId.generate());
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/handling/manual-update")
                        .with(csrf())
                        .param("bookingId", bookingId.toString())
                        .param("eventType", "MANUAL_UPDATE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00")
                        .param("memo", "台風のため保管中"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/handling?bookingId=" + bookingId))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /handling/manual-update でメモが空の場合はフィールドエラーを返す")
    void createManualUpdate_emptyMemo_showsFieldError() throws Exception {
        mockMvc.perform(post("/handling/manual-update")
                        .with(csrf())
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("eventType", "MANUAL_UPDATE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00")
                        .param("memo", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/manual-update"))
                .andExpect(model().attributeHasFieldErrors("form", "memo"));
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
