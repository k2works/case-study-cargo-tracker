package com.example.bookingms.infrastructure.routing;

import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateFinder;
import com.example.bookingms.application.port.RouteCandidateQuery;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * 経路候補を routingms へ取りに行く ACL（[ADR-019]）。
 *
 * <p>routingms の型はここから先へ出さない。{@link RouteCandidateResponse} で受け、
 * Booking Context の {@link CargoItinerary} へ変換する。
 *
 * <p><strong>利用者ヘッダ（[ADR-007]）は伝播しない。</strong>この呼び出しは
 * 「システムが経路候補を引く」ものであり、利用者の代理ではない。伝播すると、
 * routingms 側の認可が「呼び出し元の利用者が経路設計者か」を見ることになり、
 * bookingms の中で完結する処理（確定時の再検証）がロールに依存する。
 * サービス間の信頼はネットワーク境界（Gateway より内側）で担保する。
 */
public class RestRouteCandidateFinder implements RouteCandidateFinder {

    private final RestClient restClient;
    private final LocationRepository locations;

    public RestRouteCandidateFinder(RestClient restClient, LocationRepository locations) {
        this.restClient = restClient;
        this.locations = locations;
    }

    @Override
    public List<CargoItinerary> find(RouteCandidateQuery query) {
        RouteCandidateResponse response = restClient.get()
                .uri(uriBuilder -> uriOf(uriBuilder, query))
                .retrieve()
                .body(RouteCandidateResponse.class);

        if (response == null || response.candidates() == null) {
            return List.of();
        }
        return response.candidates().stream()
                .map(this::toItinerary)
                .flatMap(Optional::stream)
                .toList();
    }

    private java.net.URI uriOf(UriBuilder uriBuilder, RouteCandidateQuery query) {
        uriBuilder.path("/api/v1/routes")
                .queryParam("origin", query.originUnLocode())
                .queryParam("destination", query.destinationUnLocode())
                // 期限は日付のまま渡す。日時への変換は相手が業務タイムゾーンで行う（ADR-017）
                .queryParam("deadline", query.arrivalDeadline().toString())
                .queryParam("cargoType", query.cargoType().name());
        if (query.maxTransshipments() != null) {
            uriBuilder.queryParam("maxTransshipments", query.maxTransshipments());
        }
        if (query.earliestDeparture() != null) {
            uriBuilder.queryParam("earliestDeparture", query.earliestDeparture().toString());
        }
        return uriBuilder.build();
    }

    /**
     * 候補 1 件を旅程へ変換する。
     *
     * <p><strong>地点はマスタから引く。</strong>相手が返した名称をそのまま使うと、地点名の
     * 直しがマスタと予約の 2 か所に分かれる。知らない地点が混ざっていた候補は<strong>落とす</strong>
     * （変換できないものを黙って部分的に組み立てると、つながっていない旅程ができる）。
     */
    private Optional<CargoItinerary> toItinerary(RouteCandidateResponse.Candidate candidate) {
        if (candidate.legs() == null || candidate.legs().isEmpty()) {
            return Optional.empty();
        }
        List<Leg> legs = candidate.legs().stream()
                .map(this::toLeg)
                .flatMap(Optional::stream)
                .toList();
        if (legs.size() != candidate.legs().size()) {
            return Optional.empty();
        }
        return Optional.of(CargoItinerary.of(legs));
    }

    private Optional<Leg> toLeg(RouteCandidateResponse.CandidateLeg leg) {
        Optional<Location> from = locations.findByUnLocode(leg.fromUnLocode());
        Optional<Location> to = locations.findByUnLocode(leg.toUnLocode());
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Leg.of(VoyageNumber.of(leg.voyageNumber()), from.get(), to.get(),
                leg.departureTime(), leg.arrivalTime()));
    }
}
