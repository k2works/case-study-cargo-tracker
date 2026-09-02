package com.example.bookingms.infrastructure.acl;

import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateFinder;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateQuery;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateUnavailableException;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.VoyageNumber;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
 *
 * <p>ただし<strong>名乗りはする</strong>。相手の [ADR-007] フィルタは利用者ヘッダの無い
 * 要求を一律に断るため、何も付けないと経路を確定する瞬間にだけ必ず 401 になる
 * （IT5 はこの状態で、実環境の往復を通すまで誰も気づかなかった）。名乗るのは
 * 呼び出し元の利用者ではなく、システム自身である。ロールは付けない。
 */
public class RestRouteCandidateFinder implements RouteCandidateFinder {

    /**
     * このサービス自身を表す主体。
     *
     * <p>利用者 ID と取り違えられない形にする。利用者と同じ見た目にすると、監査ログで
     * 「誰がやったのか」が分からなくなる。
     */
    public static final String SYSTEM_PRINCIPAL = "system:bookingms";

    private final RestClient restClient;
    private final LocationRepository locations;

    public RestRouteCandidateFinder(RestClient restClient, LocationRepository locations) {
        this.restClient = restClient;
        this.locations = locations;
    }

    @Override
    public List<CargoItinerary> find(RouteCandidateQuery query) {
        RouteCandidateResponse response;
        try {
            // catch は呼び出しだけを囲む。変換まで囲むと、地点マスタ側の不具合まで
            // 「経路を確認できません」に化けて原因が消える
            response = restClient.get()
                    .uri(uriBuilder -> uriOf(uriBuilder, query))
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(RouteCandidateResponse.class);
        } catch (RestClientException e) {
            // 「確認できなかった」と「候補に無かった」は違う。空のリストを返すと、
            // 呼び出し側は「航海スケジュールが変わった」と誤診し、経路設計者は
            // 何度探し直しても直らない作業に入る
            throw new RouteCandidateUnavailableException(
                    "いま経路を確認できません。しばらくしてからもう一度お試しください", e);
        }

        if (response == null || response.candidates() == null) {
            return List.of();
        }
        // 地点は 1 回だけ引く。区間ごとに引くと、確定 1 回あたり
        // 候補数 × 区間数 × 2（積込地と荷降し地）の問い合わせになる。
        // 地点は数十件のマスタであり、まとめて読んで引き当てるほうが安い（IT5 レビュー 低 33）
        Map<String, Location> byUnLocode = locations.findAll().stream()
                .collect(Collectors.toMap(Location::unLocode, location -> location,
                        (first, ignored) -> first));
        return response.candidates().stream()
                .map(candidate -> toItinerary(candidate, byUnLocode))
                .flatMap(Optional::stream)
                .toList();
    }

    private java.net.URI uriOf(UriBuilder uriBuilder, RouteCandidateQuery query) {
        uriBuilder.path("/api/v1/routes")
                .queryParam("origin", query.originUnLocode())
                .queryParam("destination", query.destinationUnLocode())
                // 期限は日付のまま渡す。日時への変換は相手が業務タイムゾーンで行う（ADR-017）
                .queryParam("deadline", query.arrivalDeadline().toString())
                .queryParam("cargoType", query.cargoType().name())
                // **再設計では期限で弾かない**（US28-4）。伝えなければ相手が既定で刈る
                .queryParam("reroute", query.reroute());
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
    private Optional<CargoItinerary> toItinerary(RouteCandidateResponse.Candidate candidate,
            Map<String, Location> byUnLocode) {
        if (candidate.legs() == null || candidate.legs().isEmpty()) {
            return Optional.empty();
        }
        List<Leg> legs = candidate.legs().stream()
                .map(leg -> toLeg(leg, byUnLocode))
                .flatMap(Optional::stream)
                .toList();
        if (legs.size() != candidate.legs().size()) {
            return Optional.empty();
        }
        return Optional.of(CargoItinerary.of(legs));
    }

    private Optional<Leg> toLeg(RouteCandidateResponse.CandidateLeg leg,
            Map<String, Location> byUnLocode) {
        Location from = byUnLocode.get(leg.fromUnLocode());
        Location to = byUnLocode.get(leg.toUnLocode());
        if (from == null || to == null) {
            return Optional.empty();
        }
        return Optional.of(Leg.of(VoyageNumber.of(leg.voyageNumber()), from, to,
                leg.departureTime(), leg.arrivalTime()));
    }
}
