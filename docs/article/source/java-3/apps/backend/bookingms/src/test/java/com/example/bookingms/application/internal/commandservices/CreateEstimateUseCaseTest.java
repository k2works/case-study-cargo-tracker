package com.example.bookingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bookingms.application.internal.outboundservices.acl.ChargeQuoteFinder;
import com.example.bookingms.domain.repository.EstimateRepository;
import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateFinder;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.aggregates.Estimate;
import com.example.bookingms.domain.model.valueobjects.EstimateNumber;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.commands.CreateEstimateCommand;

/**
 * 輸送見積を作る（US01）。
 *
 * <p>ここで確かめるのは 3 つである。
 * <ul>
 *   <li><strong>概算料金を自分で計算していない</strong>（[ADR-028] 決定 6）
 *   <li><strong>「候補が 0 件」と「間に合う候補が 0 件」を区別している</strong>（01-5）
 *   <li><strong>候補の 4 項目が揃っている</strong>（01-3・IT11 Try 2）
 * </ul>
 */
@DisplayName("輸送見積の作成")
class CreateEstimateUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZONE);

    private static final LocalDate DEADLINE = LocalDate.parse("2027-02-28");

    private RouteCandidateFinder routes;

    private ChargeQuoteFinder quotes;

    private LocationRepository locations;

    private EstimateRepository estimates;

    private CreateEstimateUseCase useCase;

    @BeforeEach
    void setUp() {
        routes = mock(RouteCandidateFinder.class);
        quotes = mock(ChargeQuoteFinder.class);
        locations = mock(LocationRepository.class);
        estimates = mock(EstimateRepository.class);
        useCase = new CreateEstimateUseCase(routes, quotes, locations, estimates, CLOCK);

        when(locations.regionsByUnLocode()).thenReturn(Map.of(
                "JPTYO", "DOMESTIC", "SGSIN", "NEAR_SEA", "USLAX", "OCEAN"));
        when(estimates.nextNumber()).thenReturn(EstimateNumber.of("EST-2026000001"));
    }

    private static CargoItinerary itinerary(String voyage, String arrival, String... ports) {
        List<Leg> legs = new java.util.ArrayList<>();
        Instant departure = Instant.parse("2027-01-10T00:00:00Z");
        for (int i = 0; i < ports.length - 1; i++) {
            legs.add(Leg.restore(VoyageNumber.of(voyage),
                    Location.of(ports[i], ports[i]),
                    Location.of(ports[i + 1], ports[i + 1]),
                    departure.plus(java.time.Duration.ofDays(i)),
                    i == ports.length - 2 ? Instant.parse(arrival)
                            : departure.plus(java.time.Duration.ofDays(i + 1))));
        }
        return CargoItinerary.restore(legs);
    }

    private static CreateEstimateCommand command() {
        return new CreateEstimateCommand("JPTYO", "USLAX", DEADLINE, "GENERAL",
                new BigDecimal("4200"));
    }

    @Nested
    @DisplayName("候補の試算")
    class Quoting {

        /**
         * <strong>概算料金は billingms が出す</strong>（決定 6）。
         *
         * <p>ここで計算すると、荷主に出した見積と請求が違う金額になる。
         */
        @Test
        @DisplayName("概算料金は試算のポートから受け取る（自分で計算しない）")
        void takesTheCostFromThePort() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-01-20T00:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1260000"));

            EstimateQuote quote = useCase.quote(command());

            assertThat(quote.candidates()).hasSize(1);
            assertThat(quote.candidates().get(0).estimatedCost()).isEqualByComparingTo("1260000");
            // **区間の地域区分を渡している**——係数は渡さない（式は相手が持つ）
            verify(quotes).quote(
                    List.of(new ChargeQuoteFinder.QuoteLeg("DOMESTIC", "OCEAN")),
                    new BigDecimal("4200"), "GENERAL");
        }

        /** <strong>4 項目が揃う</strong>（01-3）。1 つ欠けても字面は満たす。 */
        @Test
        @DisplayName("候補は航海番号・経由港・所要日数・概算料金を持つ")
        void fillsTheFourFields() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V002", "2027-01-25T00:00:00Z", "JPTYO", "SGSIN", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("500000"));

            var candidate = useCase.quote(command()).candidates().get(0);

            assertThat(candidate.voyageNumber()).isEqualTo("V002");
            assertThat(candidate.transitPort()).isEqualTo("SGSIN");
            assertThat(candidate.transitDays()).isEqualTo(15);
            assertThat(candidate.estimatedCost()).isEqualByComparingTo("500000");
        }

        /** 直行は経由港を持たない。 */
        @Test
        @DisplayName("直行の候補は経由港を持たない")
        void directCandidatesHaveNoTransitPort() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-01-20T00:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1260000"));

            assertThat(useCase.quote(command()).candidates().get(0).direct()).isTrue();
        }

        /**
         * <strong>知らない港は断る。</strong>
         *
         * <p>既定値（国内）に倒すと、地点マスタに足し忘れた港を通る経路だけが
         * 安く見積もられる——名簿方式は載っていないものを通すと、載せ忘れたものほど漏れる。
         */
        @Test
        @DisplayName("地域区分を決めていない港が経路にあれば断る")
        void rejectsPortsWithoutARegion() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V003", "2027-01-20T00:00:00Z", "JPTYO", "AUMEL")));

            // **依頼はラムダの外で組む。**中で組むと、例外を投げたのが依頼の
            // 組み立てか試算かを判別できない
            CreateEstimateCommand command = command();

            assertThatThrownBy(() -> useCase.quote(command))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("期限に間に合わないとき（受入基準 01-5）")
    class WhenNothingArrivesInTime {

        /**
         * <strong>「候補が 0 件」と「間に合う候補が 0 件」は別である。</strong>
         *
         * <p>後者は「最短でも N 日超過します」と言える——荷主に折り返す言葉がある。
         */
        @Test
        @DisplayName("間に合う候補が無ければ、最短の超過日数を返す")
        void reportsTheShortestOverrun() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-03-10T00:00:00Z", "JPTYO", "USLAX"),
                    itinerary("V002", "2027-03-05T00:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1"));

            EstimateQuote quote = useCase.quote(command());

            assertThat(quote.hasCandidates()).isFalse();
            assertThat(quote.daysExceeded())
                    .as("最短でも何日超過するかを言えていない。営業担当者は荷主に何と言えばよいか分からない")
                    .isEqualTo(5);
        }

        /** 候補そのものが無ければ、超過日数も言えない（黙って 0 日と言わない）。 */
        @Test
        @DisplayName("候補が 1 本も無ければ、超過日数は持たない")
        void hasNoOverrunWhenThereIsNoRouteAtAll() {
            when(routes.find(any())).thenReturn(List.of());

            EstimateQuote quote = useCase.quote(command());

            assertThat(quote.hasCandidates()).isFalse();
            assertThat(quote.daysExceeded()).isNull();
        }

        /** 間に合う候補が 1 本でもあれば、超過日数は出さない。 */
        @Test
        @DisplayName("間に合う候補があれば、超過日数は出さない")
        void hidesTheOverrunWhenSomethingArrivesInTime() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-03-10T00:00:00Z", "JPTYO", "USLAX"),
                    itinerary("V002", "2027-02-20T00:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1"));

            EstimateQuote quote = useCase.quote(command());

            assertThat(quote.candidates()).hasSize(1);
            assertThat(quote.daysExceeded()).isNull();
        }

        /**
         * <strong>期限当日の到着は間に合っている。</strong>
         *
         * <p>日付で比べる——時刻付きで比べると、期限当日に着く便が「遅れる」ことになる。
         */
        @Test
        @DisplayName("期限当日に着く便は、間に合う候補である")
        void treatsArrivalOnTheDeadlineAsInTime() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-02-28T12:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1"));

            assertThat(useCase.quote(command()).candidates()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("作成")
    class Creating {

        @Test
        @DisplayName("見積番号を採番して保存する")
        void savesWithANumber() {
            when(routes.find(any())).thenReturn(List.of(
                    itinerary("V001", "2027-01-20T00:00:00Z", "JPTYO", "USLAX")));
            when(quotes.quote(any(), any(), anyString())).thenReturn(new BigDecimal("1260000"));

            Estimate estimate = useCase.create(command());

            assertThat(estimate.estimateNumber().value()).isEqualTo("EST-2026000001");
            assertThat(estimate.candidates()).hasSize(1);
            verify(estimates).save(estimate);
        }

        /** 入力に誤りがあれば、採番も保存もしない。 */
        @Test
        @DisplayName("同じ港への見積は作らない")
        void rejectsTheSameOriginAndDestination() {
            when(routes.find(any())).thenReturn(List.of());

            CreateEstimateCommand sameHarbour = new CreateEstimateCommand(
                    "JPTYO", "JPTYO", DEADLINE, "GENERAL", new BigDecimal("4200"));

            assertThatThrownBy(() -> useCase.create(sameHarbour))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(estimates, never()).save(any());
        }
    }
}
