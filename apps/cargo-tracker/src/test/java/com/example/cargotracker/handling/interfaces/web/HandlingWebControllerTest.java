package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    @DisplayName("GET /handling/new は荷役作業記録フォームを表示する")
    void showForm_returnsNewView() throws Exception {
        mockMvc.perform(get("/handling/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/new"))
                .andExpect(model().attributeExists("form", "eventTypes"));
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

    @Test
    @DisplayName("POST /handling は正常に記録してリダイレクトする")
    void record_success_redirectsWithMessage() throws Exception {
        when(recordHandlingEventCommandService.execute(any()))
                .thenReturn(HandlingEventId.generate());

        mockMvc.perform(post("/handling")
                        .with(csrf())
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("eventType", "LOAD")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2025-01-15T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/handling/new"))
                .andExpect(flash().attributeExists("successMessage"));
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
}
