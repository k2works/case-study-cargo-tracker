package com.example.cargotracker.shipper.interfaces.rest;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryNotFoundException;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipperRestController.class)
@WithMockUser
@DisplayName("ShipperRestController")
class ShipperRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterShipperCommandService registerShipperCommandService;

    @MockitoBean
    private FindShipperQueryService findShipperQueryService;

    @Test
    @DisplayName("荷主一覧を JSON で取得できる")
    void list() throws Exception {
        Shipper shipper = anyShipper();
        when(findShipperQueryService.findAll()).thenReturn(List.of(shipper));

        mockMvc.perform(get("/api/shippers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shipper.getId().toString()))
                .andExpect(jsonPath("$[0].email").value("yamada@example.com"));
    }

    @Test
    @DisplayName("荷主詳細を JSON で取得できる")
    void detail() throws Exception {
        Shipper shipper = anyShipper();
        when(findShipperQueryService.execute(shipper.getId())).thenReturn(shipper);

        mockMvc.perform(get("/api/shippers/" + shipper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipper.getId().toString()))
                .andExpect(jsonPath("$.name").value("山田 太郎"));
    }

    @Test
    @DisplayName("荷主登録 API は 201 と Location を返す")
    void register() throws Exception {
        Shipper shipper = anyShipper();
        when(registerShipperCommandService.execute(any())).thenReturn(shipper.getId());
        when(findShipperQueryService.execute(shipper.getId())).thenReturn(shipper);

        mockMvc.perform(post("/api/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "山田 太郎",
                                  "email": "yamada@example.com",
                                  "phone": "03-0000-0000",
                                  "category": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/shippers/" + shipper.getId()))
                .andExpect(jsonPath("$.id").value(shipper.getId().toString()));
    }

    @Test
    @DisplayName("存在しない荷主は 404 を返す")
    void notFound() throws Exception {
        ShipperId shipperId = ShipperId.generate();
        when(findShipperQueryService.execute(shipperId))
                .thenThrow(new ShipperQueryNotFoundException(shipperId.toString()));

        mockMvc.perform(get("/api/shippers/" + shipperId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("荷主が見つかりません: " + shipperId));
    }

    @Test
    @DisplayName("重複メールは 400 を返す")
    void duplicateEmail() throws Exception {
        when(registerShipperCommandService.execute(any()))
                .thenThrow(new DuplicateShipperException(ShipperId.generate()));

        mockMvc.perform(post("/api/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "山田 太郎",
                                  "email": "yamada@example.com",
                                  "phone": "03-0000-0000",
                                  "category": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    private Shipper anyShipper() {
        return Shipper.registerIndividual(
                ShipperId.generate(),
                new ShipperName("山田 太郎"),
                new ContactInfo("yamada@example.com", "03-0000-0000")
        );
    }
}
