package com.example.cargotracker.exception.interfaces.web;

import com.example.cargotracker.exception.application.internal.commandservices.RecordCargoExceptionCommandService;
import com.example.cargotracker.exception.application.internal.commandservices.TrackingNotFoundException;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ExceptionWebController.class)
@WithMockUser
@DisplayName("ExceptionWebController")
class ExceptionWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordCargoExceptionCommandService recordCargoExceptionCommandService;

    @Test
    @DisplayName("GET /exceptions/new - 例外記録フォームを表示できる")
    void showForm_returnsNewView() throws Exception {
        mockMvc.perform(get("/exceptions/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception/new"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attributeExists("exceptionTypes"));
    }

    @Test
    @DisplayName("GET /exceptions/new?trackingNumber= - 追跡番号を事前設定した状態でフォームを表示できる")
    void showForm_withTrackingNumber_presetsField() throws Exception {
        mockMvc.perform(get("/exceptions/new").param("trackingNumber", "TRK-AB123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception/new"));
    }

    @Test
    @DisplayName("POST /exceptions/new - 遅延例外を記録するとリダイレクトされ成功メッセージが設定される")
    void createException_delay_redirectsWithSuccessMessage() throws Exception {
        when(recordCargoExceptionCommandService.execute(any())).thenReturn(ExceptionId.generate());

        mockMvc.perform(post("/exceptions/new")
                        .with(csrf())
                        .param("trackingNumber", "TRK-AB123456")
                        .param("exceptionType", "DELAY")
                        .param("locationCode", "JPTYO")
                        .param("occurredAt", "2026-05-28T10:00")
                        .param("reason", "悪天候")
                        .param("resolution", "代替船を手配"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("荷主への通知を手動で行ってください")));
    }

    @Test
    @DisplayName("POST /exceptions/new - 破損例外を記録するとリダイレクトされ成功メッセージが設定される")
    void createException_damage_redirectsWithSuccessMessage() throws Exception {
        when(recordCargoExceptionCommandService.execute(any())).thenReturn(ExceptionId.generate());

        mockMvc.perform(post("/exceptions/new")
                        .with(csrf())
                        .param("trackingNumber", "TRK-AB123456")
                        .param("exceptionType", "DAMAGE")
                        .param("locationCode", "USNYC")
                        .param("occurredAt", "2026-05-30T14:00")
                        .param("reason", "積み降ろし中に破損")
                        .param("resolution", "補償手続きを開始"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("荷主への通知を手動で行ってください")));
    }

    @Test
    @DisplayName("POST /exceptions/new - 紛失例外を記録すると緊急フラグメッセージが設定される")
    void createException_loss_redirectsWithUrgentMessage() throws Exception {
        when(recordCargoExceptionCommandService.execute(any())).thenReturn(ExceptionId.generate());

        mockMvc.perform(post("/exceptions/new")
                        .with(csrf())
                        .param("trackingNumber", "TRK-AB123456")
                        .param("exceptionType", "LOSS")
                        .param("locationCode", "SGSIN")
                        .param("occurredAt", "2026-05-31T08:00")
                        .param("reason", "保管中に紛失")
                        .param("resolution", "調査を開始"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("管理担当者への通知を手動で行ってください")));
    }

    @Test
    @DisplayName("POST /exceptions/new - バリデーションエラーがある場合はフォームを再表示する")
    void createException_validationError_returnsForm() throws Exception {
        mockMvc.perform(post("/exceptions/new")
                        .with(csrf())
                        .param("trackingNumber", "")
                        .param("exceptionType", "DELAY")
                        .param("occurredAt", "2026-05-28T10:00")
                        .param("resolution", "対応内容"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception/new"))
                .andExpect(model().attributeHasFieldErrors("form", "trackingNumber"));
    }

    @Test
    @DisplayName("POST /exceptions/new - 存在しない追跡番号の場合はエラーメッセージを表示する")
    void createException_unknownTrackingNumber_showsError() throws Exception {
        doThrow(new TrackingNotFoundException("TRK-NOT-FOUND"))
                .when(recordCargoExceptionCommandService).execute(any());

        mockMvc.perform(post("/exceptions/new")
                        .with(csrf())
                        .param("trackingNumber", "TRK-NOT-FOUND")
                        .param("exceptionType", "DELAY")
                        .param("locationCode", "JPTYO")
                        .param("occurredAt", "2026-05-28T10:00")
                        .param("reason", "悪天候")
                        .param("resolution", "対応内容"))
                .andExpect(status().isOk())
                .andExpect(view().name("exception/new"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}
