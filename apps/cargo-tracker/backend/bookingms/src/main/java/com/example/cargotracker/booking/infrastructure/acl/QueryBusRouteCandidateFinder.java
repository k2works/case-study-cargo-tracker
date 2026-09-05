package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.shared.contract.query.FindRouteCandidatesQuery;
import com.example.cargotracker.shared.contract.query.RouteCandidateDto;
import com.example.cargotracker.shared.contract.query.RouteCandidatesResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import org.springframework.stereotype.Component;

/**
 * 経路候補の ACL（US08）。routingms へ Axon Query Bus 越しに問い合わせる。
 *
 * <p><b>契約 DTO を自 BC の型へ組み直す。</b> {@code RouteCandidateDto} を画面まで
 * そのまま運ぶと、routingms の応答の形が bookingms の画面の形になる。</p>
 *
 * <p><b>落ちているときに空リストを返さない。</b> 空にすると「候補が無い」と読まれ、
 * 経路設計者は条件を変え続ける。{@link RouteCandidateFinder.RouteSearchUnavailable} を
 * 投げ、Controller が 503 にする。</p>
 *
 * <p>タイムアウトは {@link QueryDispatcher} の 5 秒（architecture_backend.md）。
 * ここで独自に待ち方を決めると、同じ問い合わせが呼ぶ場所によって別の待ち方になる。</p>
 */
@Component
public class QueryBusRouteCandidateFinder implements RouteCandidateFinder {

    private final QueryDispatcher queries;

    public QueryBusRouteCandidateFinder(QueryDispatcher queries) {
        this.queries = queries;
    }

    @Override
    public RouteCandidates find(RouteSearchRequest request) {
        RouteCandidatesResponse response;
        try {
            response = queries.query(toQuery(request), RouteCandidatesResponse.class);
        } catch (BusinessRuleViolation e) {
            // 経路設計側が「知らない港・種別」と断った。障害ではないので、そのまま通す。
            throw e;
        } catch (RuntimeException e) {
            // 相手が居ない（NoHandlerForQueryException）・時間切れ・通信の失敗。
            throw new RouteSearchUnavailable("経路設計サービスに問い合わせられませんでした", e);
        }
        if (response == null) {
            // ハンドラが null を返すことはないが、返ったなら「0 件」ではない。
            throw new RouteSearchUnavailable("経路設計サービスから応答がありません", null);
        }
        return new RouteCandidates(
                response.candidates().stream()
                        .map(QueryBusRouteCandidateFinder::toCandidate)
                        .toList(),
                response.truncated());
    }

    private static FindRouteCandidatesQuery toQuery(RouteSearchRequest request) {
        return new FindRouteCandidatesQuery(
                request.origin().unLocode().value(),
                request.destination().unLocode().value(),
                request.arrivalDeadline(),
                request.cargoType().name(),
                request.excludePorts().stream()
                        .map(port -> port.unLocode().value())
                        .toList(),
                request.departFrom() == null
                        ? null : request.departFrom().unLocode().value());
    }

    private static RouteCandidate toCandidate(RouteCandidateDto dto) {
        return new RouteCandidate(
                dto.legs().stream().map(QueryBusRouteCandidateFinder::toLeg).toList(),
                dto.transitDays(),
                dto.direct());
    }

    private static Leg toLeg(RouteCandidateDto.LegDto leg) {
        return new Leg(leg.voyageNumber(), Location.of(leg.loadUnLocode()),
                Location.of(leg.unloadUnLocode()), leg.loadTime(), leg.unloadTime());
    }
}
