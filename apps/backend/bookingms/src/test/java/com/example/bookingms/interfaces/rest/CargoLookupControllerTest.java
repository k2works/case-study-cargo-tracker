package com.example.bookingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.CargoSnapshotContract;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 追跡番号で貨物を引く入口（US15-1・[ADR-023] 決定 2）。
 *
 * <p>呼ぶのは handlingms であり、人ではない。ここで確かめるのは
 * <strong>名簿に無い主体を通さないこと</strong>と、<strong>返す内容が契約どおりであること</strong>。
 */
@WebMvcTest(CargoLookupController.class)
@DisplayName("貨物の照会 API（サービス間）")
class CargoLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CargoRepository cargoes;

    private static String pathFor(String trackingNumber) {
        return CargoSnapshotContract.PATH.replace("{trackingNumber}", trackingNumber);
    }

    private static String shipperPathFor(String trackingNumber) {
        return "/api/v1/bookings/shipper-snapshots/" + trackingNumber;
    }

    @Test
    @DisplayName("既知のサービスは、照合に要る項目を受け取れる")
    void returnsSnapshotToTrustedService() throws Exception {
        when(cargoes.findByTrackingNumber("TRK-20260823-0001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.trackingIssued(),
                        "丸紅商事")));

        mockMvc.perform(get(pathFor("TRK-20260823-0001"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                CargoSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                .andExpect(jsonPath("$.originUnLocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationUnLocode").value("USLAX"))
                .andExpect(jsonPath("$.legs[0].voyageNumber").value("V0100"))
                .andExpect(jsonPath("$.legs[0].loadUnLocode").value("JPTYO"))
                .andExpect(jsonPath("$.legs[0].unloadUnLocode").value("USLAX"));
    }

    /**
     * <strong>荷主・貨物の内容・金額は返さない。</strong>
     *
     * <p>荷役作業員に使い道が無く、渡せば渡すほど漏れたときの範囲が広がる。
     * 「返している項目が正しい」だけを見ると、余分な項目が増えても緑のままである。
     */
    @Test
    @DisplayName("照合に要らない項目は返さない")
    void doesNotLeakUnrelatedFields() throws Exception {
        when(cargoes.findByTrackingNumber(any()))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.routed(), "丸紅商事")));

        mockMvc.perform(get(pathFor("TRK-20260823-0001"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                CargoSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(jsonPath("$.shipperName").doesNotExist())
                .andExpect(jsonPath("$.shipperId").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.weightKg").doesNotExist())
                .andExpect(jsonPath("$.arrivalDeadline").doesNotExist());
    }

    /**
     * 名簿に無い主体は通さない。
     *
     * <p>人のロールでも開かない。<strong>追跡番号を順に試せば実在する予約が分かる</strong>。
     */
    @ParameterizedTest
    @ValueSource(strings = {"sales01", "handler01", "system:trackingms", "system:bookingms"})
    @DisplayName("名簿に無い主体は断り、DB も引かない")
    void rejectsUnknownPrincipals(String principal) throws Exception {
        mockMvc.perform(get(pathFor("TRK-20260823-0001"))
                        .header(AuthenticatedUser.USER_ID_HEADER, principal))
                .andExpect(status().isForbidden());

        verify(cargoes, never()).findByTrackingNumber(any());
    }

    @Test
    @DisplayName("名乗らない要求は 400（ADR-007 のフィルタと同じ扱い）")
    void rejectsRequestWithoutPrincipal() throws Exception {
        mockMvc.perform(get(pathFor("TRK-20260823-0001")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("見つからない追跡番号は 404")
    void reportsNotFound() throws Exception {
        when(cargoes.findByTrackingNumber(any())).thenReturn(Optional.empty());

        mockMvc.perform(get(pathFor("TRK-99999999-9999"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                CargoSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("trackingms は自社境界の判定に要る荷主 ID を受け取れる")
    void returnsShipperSnapshotToTrackingms() throws Exception {
        when(cargoes.findByTrackingNumber("TRK-20260823-0001"))
                .thenReturn(Optional.of(new CargoSummary(BookingTestCargoes.trackingIssued(),
                        "丸紅商事")));

        mockMvc.perform(get(shipperPathFor("TRK-20260823-0001"))
                        .header(AuthenticatedUser.USER_ID_HEADER, "system:trackingms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BKG-2026000001"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-20260823-0001"))
                .andExpect(jsonPath("$.shipperId").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"system:handlingms", "system:bookingms", "sales01", "shipper01"})
    @DisplayName("trackingms 以外は荷主 ID 付き Snapshot を読めない")
    void rejectsShipperSnapshotForOtherPrincipals(String principal) throws Exception {
        mockMvc.perform(get(shipperPathFor("TRK-20260823-0001"))
                        .header(AuthenticatedUser.USER_ID_HEADER, principal))
                .andExpect(status().isForbidden());

        verify(cargoes, never()).findByTrackingNumber(any());
    }

    /**
     * <strong>名簿を手で書かない。</strong>
     *
     * <p>手書きの名簿は、こちらが項目を足しても赤にならない。足した項目を handlingms が
     * 読めているかは誰も確かめておらず、実物でだけ null になる。
     */
    @Test
    @DisplayName("返す項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        org.assertj.core.api.Assertions.assertThat(
                        java.util.Arrays.stream(CargoSnapshotResponse.class.getRecordComponents())
                                .map(java.lang.reflect.RecordComponent::getName).toList())
                .as("返す項目が変わった。handlingms 側の受け皿も直すこと")
                .containsExactlyElementsOf(CargoSnapshotContract.FIELDS);

        org.assertj.core.api.Assertions.assertThat(
                        java.util.Arrays.stream(CargoSnapshotResponse.LegSnapshotResponse.class
                                        .getRecordComponents())
                                .map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactlyElementsOf(CargoSnapshotContract.LEG_FIELDS);
    }
}
