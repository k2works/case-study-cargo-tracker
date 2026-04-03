package com.example.cargotracker.exception.interfaces.rest;

import com.example.cargotracker.exception.application.internal.commandservices.RecordCargoExceptionCommandService;
import com.example.cargotracker.exception.application.internal.commandservices.TrackingNotFoundException;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.exception.interfaces.rest.dto.CargoExceptionResponse;
import com.example.cargotracker.exception.interfaces.rest.dto.RecordCargoExceptionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CargoExceptionRestController.class)
@WithMockUser
@DisplayName("CargoExceptionRestController")
class CargoExceptionRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordCargoExceptionCommandService recordCargoExceptionCommandService;

    @Test
    @DisplayName("遅延例外を記録すると 201 と Location ヘッダーが返る")
    void record_returns201() throws Exception {
        ExceptionId generatedId = ExceptionId.generate();
        when(recordCargoExceptionCommandService.execute(any())).thenReturn(generatedId);

        String requestBody = """
                {
                  "trackingNumber": "TRK-AB123456",
                  "exceptionType": "DELAY",
                  "locationCode": "JPTYO",
                  "occurredAt": "2026-05-28T10:00:00",
                  "reason": "悪天候"
                }
                """;

        mockMvc.perform(post("/api/v1/cargo-exceptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-AB123456"))
                .andExpect(jsonPath("$.exceptionType").value("DELAY"));
    }

    @Test
    @DisplayName("必須フィールドが欠如している場合は 400 を返す")
    void record_missingFields_returns400() throws Exception {
        String requestBody = """
                {
                  "locationCode": "JPTYO"
                }
                """;

        mockMvc.perform(post("/api/v1/cargo-exceptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("存在しない追跡番号の場合は 404 を返す")
    void record_unknownTrackingNumber_returns404() throws Exception {
        doThrow(new TrackingNotFoundException("TRK-NOT-FOUND"))
                .when(recordCargoExceptionCommandService).execute(any());

        String requestBody = """
                {
                  "trackingNumber": "TRK-NOT-FOUND",
                  "exceptionType": "DELAY",
                  "locationCode": "JPTYO",
                  "occurredAt": "2026-05-28T10:00:00",
                  "reason": "悪天候"
                }
                """;

        mockMvc.perform(post("/api/v1/cargo-exceptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }
}
