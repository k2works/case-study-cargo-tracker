package com.example.trackingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.internal.TrackingLookupUseCase;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingLookupLogger;
import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingNotice;
import com.example.trackingms.domain.model.TrackingNumber;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 公開の追跡照会（US18・[ADR-024] 決定 5・7）。
 *
 * <p>ここで確かめるのは<strong>何を出さないか</strong>である。認証が無い以上、
 * 追跡番号を手に入れた誰もが見る。
 */
@WebMvcTest(PublicTrackingController.class)
@Import(PublicTrackingControllerTest.UseCaseConfig.class)
@DisplayName("公開の追跡照会（US18）")
class PublicTrackingControllerTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final String NUMBER = "TRK-20260823-0001";

    @org.springframework.boot.test.context.TestConfiguration
    static class UseCaseConfig {
        @org.springframework.context.annotation.Bean
        TrackingLookupUseCase trackingLookupUseCase(TrackingActivityRepository activities,
                TrackingLookupLogger logger) {
            return new TrackingLookupUseCase(activities, logger);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingActivityRepository activities;

    @MockitoBean
    private TrackingLookupLogger lookupLogger;

    @MockitoBean
    private TrackingNoticeRepository notices;

    private static TrackingActivity received() {
        return TrackingActivity.start(TrackingNumber.of(NUMBER),
                        TrackingBookingId.of("BKG-2026000004"), TOKYO, LOS_ANGELES,
                        LocalDate.of(2027, Month.OCTOBER, 20))
                .afterHandling("RECEIVE", "JPTYO")
                .withEstimatedArrival(LocalDate.of(2027, Month.SEPTEMBER, 15));
    }

    /** US18-1・US18-2。 */
    @Test
    @DisplayName("ログインしていなくても、状態・現在地・到着予定日を返す")
    void returnsTheCargoWithoutAuthentication() throws Exception {
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.of(received()));
        when(activities.findEvents(any(), anyInt())).thenReturn(List.of());
        when(notices.findByTrackingNumber(any(), anyInt())).thenReturn(List.of());

        // **利用者ヘッダを付けずに呼ぶ。**荷主はログインしない
        mockMvc.perform(get("/api/v1/public/tracking/" + NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.statusLabel").value("受領済み"))
                .andExpect(jsonPath("$.locationName").value("Tokyo"))
                .andExpect(jsonPath("$.estimatedArrival").value("2027-09-15"));
    }

    /**
     * US18-2。<strong>分からなければ空で返す</strong>。
     *
     * <p>0 や今日で埋めると、荷主は「今日着く」と読む。
     */
    @Test
    @DisplayName("経路が決まっていなければ、到着予定日は空で返す")
    void saysUndecidedWhenNoRouteIsAssigned() throws Exception {
        TrackingActivity notRouted = TrackingActivity.start(TrackingNumber.of(NUMBER),
                TrackingBookingId.of("BKG-2026000004"), TOKYO, LOS_ANGELES,
                LocalDate.of(2027, Month.OCTOBER, 20));
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.of(notRouted));
        when(activities.findEvents(any(), anyInt())).thenReturn(List.of());
        when(notices.findByTrackingNumber(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/public/tracking/" + NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedArrival").doesNotExist());
    }

    /** US18-4。 */
    @Test
    @DisplayName("見つからない追跡番号は 404")
    void returns404ForUnknownTrackingNumber() throws Exception {
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/public/tracking/TRK-20260823-9999"))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>形式が違っても「見つかりません」で返す</strong>（[ADR-024] 決定 6）。
     *
     * <p>形式の誤りを別の答えにすると、番号の形を総当たりの手がかりとして教えることになる。
     */
    @Test
    @DisplayName("形式が違う番号も 404 で返す")
    void doesNotRevealTheNumberFormat() throws Exception {
        mockMvc.perform(get("/api/v1/public/tracking/not-a-number"))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>返す項目の名簿が、DTO の要素と一致する</strong>（[ADR-024] 決定 5）。
     *
     * <p>名簿を書き写すと、項目を足したときに検査だけが古いまま残る。
     */
    @Test
    @DisplayName("返す項目の名簿が、合意した契約と一致する")
    void returnsOnlyTheAgreedFields() {
        assertThat(Arrays.stream(PublicTrackingResponse.class.getRecordComponents())
                        .map(RecordComponent::getName).toList())
                .as("公開照会が返す項目が変わった。[ADR-024] 決定 5 を読み直すこと")
                .containsExactly("trackingNumber", "status", "statusLabel", "locationName",
                        "estimatedArrival", "hasException", "urgent", "events", "notices");
    }

    /**
     * <strong>返さないものを出していない</strong>（[ADR-024] 決定 5）。
     *
     * <p>応答の JSON 全文を見る。項目ごとに確認すると、属性が増えたときに取りこぼす。
     */
    @Test
    @DisplayName("予約番号・作業者・航海番号・例外の詳細は返さない")
    void neverExposesInternalFields() throws Exception {
        TrackingActivity withException = received()
                .raiseException(ExceptionType.LOST, "積替港で所在が確認できません",
                        Instant.parse("2027-09-03T00:00:00Z"));
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.of(withException));
        when(activities.findEvents(any(), anyInt())).thenReturn(List.of(new TrackingEvent(
                com.example.trackingms.domain.model.TrackingStatus.RECEIVED, TOKYO,
                Instant.parse("2027-09-02T00:00:00Z"), TrackingEvent.EventSource.HANDLING)));
        when(notices.findByTrackingNumber(any(), anyInt())).thenReturn(List.of());

        String body = mockMvc.perform(get("/api/v1/public/tracking/" + NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasException").value(true))
                // 紛失だけが緊急（決定 3）。表示のためだけに運ぶ値ほど、どこかの層で潰れる
                .andExpect(jsonPath("$.urgent").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).as("予約番号が荷主に見えている").doesNotContain("BKG-");
        assertThat(body).as("例外の詳細が荷主に見えている").doesNotContain("積替港");
        assertThat(body).as("荷役の種別が荷主に見えている").doesNotContain("HANDLING");
    }

    /** [ADR-024] 決定 9。お知らせは返す——**メールは送っていない**。 */
    @Test
    @DisplayName("通知した事実は、お知らせとして返す")
    void returnsNoticesInsteadOfSendingMail() throws Exception {
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.of(received()));
        when(activities.findEvents(any(), anyInt())).thenReturn(List.of());
        when(notices.findByTrackingNumber(any(), anyInt())).thenReturn(List.of(
                new TrackingNotice(Instant.parse("2027-09-02T00:00:00Z"),
                        "お荷物の状況が「受領済み」になりました。")));

        mockMvc.perform(get("/api/v1/public/tracking/" + NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices[0].message").value("お荷物の状況が「受領済み」になりました。"));
    }

    /**
     * <strong>照会は成否に関わらず記録する</strong>（[ADR-024] 決定 7）。
     *
     * <p>見つからなかった照会こそ、総当たりを見つける材料である。
     */
    @Test
    @DisplayName("見つかっても見つからなくても、照会を記録する")
    void recordsBothFoundAndNotFound() throws Exception {
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.of(received()));
        when(activities.findEvents(any(), anyInt())).thenReturn(List.of());
        when(notices.findByTrackingNumber(any(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/public/tracking/" + NUMBER)).andExpect(status().isOk());

        when(activities.findByTrackingNumber(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/public/tracking/TRK-20260823-9999"))
                .andExpect(status().isNotFound());

        verify(lookupLogger).log(anyString(), anyString(), any(), org.mockito.Mockito.eq(true));
        verify(lookupLogger).log(anyString(), anyString(), any(), org.mockito.Mockito.eq(false));
    }

    /**
     * <strong>{@code X-Forwarded-For} をそのまま信じない</strong>（[ADR-024] 決定 6）。
     *
     * <p>誰でも付けられるヘッダであり、信じると総当たりの上限をいくらでも回避できる。
     * Gateway が付けた<strong>先頭の 1 つだけ</strong>を見る。
     */
    @Test
    @DisplayName("転送元は、先頭の 1 つだけを見る")
    void takesOnlyTheFirstForwardedAddress() throws Exception {
        when(activities.findByTrackingNumber(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/public/tracking/TRK-20260823-9999")
                        .header("X-Forwarded-For", "203.0.113.10, 198.51.100.7"))
                .andExpect(status().isNotFound());

        verify(lookupLogger).log(anyString(), org.mockito.Mockito.eq("203.0.113.10"), any(),
                anyBoolean());
    }
}
