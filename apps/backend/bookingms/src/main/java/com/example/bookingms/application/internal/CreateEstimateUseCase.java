package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.ChargeQuoteFinder;
import com.example.bookingms.application.port.EstimateRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateFinder;
import com.example.bookingms.application.port.RouteCandidateQuery;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Estimate;
import com.example.bookingms.domain.model.EstimateId;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteCandidate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送見積を作る（US01）。
 *
 * <p><strong>概算料金は billingms が計算する</strong>（[ADR-028] 決定 6）。ここで
 * 式を書くと、荷主に出した見積と請求が違う金額になる。
 *
 * <p><strong>候補は routingms から引く</strong>（US09 の ACL を再利用）。終盤で
 * 新しい結合方式を発明しない。
 */
public class CreateEstimateUseCase {

    private final RouteCandidateFinder routes;

    private final ChargeQuoteFinder quotes;

    private final LocationRepository locations;

    private final EstimateRepository estimates;

    private final Clock clock;

    public CreateEstimateUseCase(RouteCandidateFinder routes, ChargeQuoteFinder quotes,
            LocationRepository locations, EstimateRepository estimates, Clock clock) {
        this.routes = routes;
        this.quotes = quotes;
        this.locations = locations;
        this.estimates = estimates;
        this.clock = clock;
    }

    /**
     * 候補を探し、それぞれの概算料金を出す（受入基準 01-2・01-3・01-5）。
     *
     * <p><strong>保存しない。</strong>営業担当者は候補を見てから作成を決める。
     */
    public EstimateQuote quote(CreateEstimateCommand command) {
        CargoType cargoType = CargoType.valueOf(command.cargoType());
        List<CargoItinerary> found = routes.find(new RouteCandidateQuery(
                command.originUnLocode(), command.destinationUnLocode(),
                command.arrivalDeadline(), cargoType, null, null,
                // **期限で弾かせない。**間に合う候補が無いときに「最短でも N 日超過」を
                // 言うには、間に合わない候補も受け取る必要がある（受入基準 01-5）
                true));

        ZoneId businessZone = clock.getZone();
        Map<String, String> regions = locations.regionsByUnLocode();

        List<RouteCandidate> inTime = new ArrayList<>();
        Integer daysExceeded = null;
        for (CargoItinerary itinerary : found) {
            LocalDate arrival = LocalDate.ofInstant(itinerary.expectedArrivalTime(), businessZone);
            if (arrival.isAfter(command.arrivalDeadline())) {
                // **最短の超過日数を覚える。**「候補が無い」で終わらせない
                int exceeded = (int) java.time.temporal.ChronoUnit.DAYS.between(
                        command.arrivalDeadline(), arrival);
                if (daysExceeded == null || exceeded < daysExceeded) {
                    daysExceeded = exceeded;
                }
                continue;
            }
            inTime.add(toCandidate(itinerary, command, regions));
        }

        return new EstimateQuote(List.copyOf(inTime), inTime.isEmpty() ? daysExceeded : null);
    }

    /**
     * 見積を作って保存する（受入基準 01-4）。
     *
     * <p><strong>候補ごと保存する。</strong>荷主に出した数字が残らないと、
     * あとから「いくらと言ったか」を確かめられない。
     */
    @Transactional
    public Estimate create(CreateEstimateCommand command) {
        EstimateQuote quote = quote(command);
        Estimate estimate = Estimate.create(EstimateId.generate(), estimates.nextNumber(),
                requirementsOf(command), quote.candidates());
        estimates.save(estimate);
        return estimate;
    }

    /** 依頼を輸送要件（5 項目）に移す。**検査は要件が持つ。** */
    private static com.example.bookingms.domain.model.EstimateRequirements requirementsOf(
            CreateEstimateCommand command) {
        return new com.example.bookingms.domain.model.EstimateRequirements(
                command.originUnLocode(), command.destinationUnLocode(),
                command.arrivalDeadline(), CargoType.valueOf(command.cargoType()),
                command.weightKg());
    }

    private RouteCandidate toCandidate(CargoItinerary itinerary, CreateEstimateCommand command,
            Map<String, String> regions) {
        List<ChargeQuoteFinder.QuoteLeg> legs = itinerary.legs().stream()
                .map(leg -> new ChargeQuoteFinder.QuoteLeg(
                        regionOf(regions, leg.loadLocation().unLocode()),
                        regionOf(regions, leg.unloadLocation().unLocode())))
                .toList();

        BigDecimal cost = quotes.quote(legs, command.weightKg(), command.cargoType());

        // **所要日数は暦の日数で数える。**荷主に「何日で着くか」を答えるための数字であり、
        // 時間で答えても意味が伝わらない
        long days = Duration.between(itinerary.expectedDepartureTime(),
                itinerary.expectedArrivalTime()).toDays();

        return new RouteCandidate(itinerary.legs().get(0).voyageNumber().value(),
                transitPortOf(itinerary), (int) days, cost);
    }

    /**
     * 経由港。**直行なら持たない**。
     *
     * <p>積み替えが複数あるときは最初の経由港を出す——候補の一覧で 1 つだけ出すなら、
     * どこで積み替えるかを最初に知りたい。
     */
    private static String transitPortOf(CargoItinerary itinerary) {
        List<Leg> legs = itinerary.legs();
        return legs.size() <= 1 ? null : legs.get(0).unloadLocation().unLocode();
    }

    /**
     * 地点の地域区分。
     *
     * <p><strong>知らない港は断る。</strong>既定値（国内）に倒すと、地点マスタに
     * 足し忘れた港を通る経路だけが安く見積もられる。
     */
    private static String regionOf(Map<String, String> regions, String unLocode) {
        String region = regions.get(unLocode);
        if (region == null) {
            throw new IllegalStateException("地域区分を決めていない地点です: " + unLocode);
        }
        return region;
    }
}
