package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ShipperWebController.class)
@WithMockUser
@DisplayName("ShipperWebController")
class ShipperWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterShipperCommandService registerShipperCommandService;

    @MockitoBean
    private FindShipperQueryService findShipperQueryService;

    @Test
    @DisplayName("荷主一覧を表示できる")
    void showList() throws Exception {
        when(findShipperQueryService.findAll()).thenReturn(List.of(anyShipper()));

        mockMvc.perform(get("/shippers"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/list"))
                .andExpect(model().attributeExists("shippers"));
    }

    @Test
    @DisplayName("荷主登録フォームを表示できる")
    void showRegisterForm() throws Exception {
        mockMvc.perform(get("/shippers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/register"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("登録成功時は一覧へリダイレクトする")
    void redirectOnSuccess() throws Exception {
        ShipperId shipperId = ShipperId.generate();
        when(registerShipperCommandService.execute(any())).thenReturn(shipperId);

        mockMvc.perform(post("/shippers")
                        .with(csrf())
                        .param("name", "山田 太郎")
                        .param("email", "yamada@example.com")
                        .param("category", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/shippers"))
                .andExpect(flash().attribute("createdShipperId", shipperId.toString()))
                .andExpect(flash().attribute("createdShipperName", "山田 太郎"));
    }

    @Test
    @DisplayName("重複メールは登録フォームに戻る")
    void duplicateEmail() throws Exception {
        when(registerShipperCommandService.execute(any()))
                .thenThrow(new DuplicateShipperException(ShipperId.generate()));

        mockMvc.perform(post("/shippers")
                        .with(csrf())
                        .param("name", "山田 太郎")
                        .param("email", "yamada@example.com")
                        .param("category", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/register"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    private Shipper anyShipper() {
        return Shipper.registerIndividual(
                ShipperId.generate(),
                new ShipperName("山田 太郎"),
                new ContactInfo("yamada@example.com", "03-0000-0000")
        );
    }
}
