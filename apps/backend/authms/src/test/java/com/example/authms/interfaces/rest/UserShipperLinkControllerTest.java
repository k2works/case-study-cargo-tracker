package com.example.authms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authms.application.internal.FindUserShipperLinkUseCase;
import com.example.authms.application.internal.UserShipperLinkResult;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.UserShipperLinkContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 利用者と荷主の紐付け照会 API（US33）。 */
@WebMvcTest(UserShipperLinkController.class)
@DisplayName("利用者と荷主の紐付け API（サービス間）")
class UserShipperLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindUserShipperLinkUseCase links;

    private static String pathFor(String username) {
        return UserShipperLinkContract.PATH.replace("{username}", username);
    }

    @Test
    @DisplayName("trackingms は利用者に紐付く荷主 ID を読める")
    void returnsLinkedShipperToTrackingms() throws Exception {
        when(links.find("shipper01")).thenReturn(UserShipperLinkResult.linked(1L));

        mockMvc.perform(get(pathFor("shipper01"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                UserShipperLinkContract.TRACKING_CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.shipperId").value(1));
    }

    @Test
    @DisplayName("bookingms も同じ契約で紐付けを読める")
    void returnsLinkedShipperToBookingms() throws Exception {
        when(links.find("shipper01")).thenReturn(UserShipperLinkResult.linked(1L));

        mockMvc.perform(get(pathFor("shipper01"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                UserShipperLinkContract.BOOKING_CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true));
    }

    @Test
    @DisplayName("未紐付け利用者は 200 と linked=false で返す")
    void returnsUnlinkedInsteadOfForbidden() throws Exception {
        when(links.find("sales01")).thenReturn(UserShipperLinkResult.unlinked());

        mockMvc.perform(get(pathFor("sales01"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                UserShipperLinkContract.TRACKING_CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.shipperId").doesNotExist());
    }

    @ParameterizedTest(name = "caller = {0}")
    @ValueSource(strings = {"sales01", "shipper01", "system:handlingms", "system:billingms"})
    @DisplayName("名簿に無い主体は紐付けを読めない")
    void rejectsUntrustedPrincipals(String caller) throws Exception {
        mockMvc.perform(get(pathFor("shipper01"))
                        .header(AuthenticatedUser.USER_ID_HEADER, caller))
                .andExpect(status().isForbidden());

        verify(links, never()).find(any());
    }

    @Test
    @DisplayName("名乗らない要求は 400")
    void rejectsRequestWithoutPrincipal() throws Exception {
        mockMvc.perform(get(pathFor("shipper01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("返す項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        org.assertj.core.api.Assertions.assertThat(
                        java.util.Arrays.stream(UserShipperLinkResponse.class.getRecordComponents())
                                .map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactlyElementsOf(UserShipperLinkContract.FIELDS);
    }
}
