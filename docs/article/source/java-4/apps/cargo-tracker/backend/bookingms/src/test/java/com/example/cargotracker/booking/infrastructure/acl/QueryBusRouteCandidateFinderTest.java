package com.example.cargotracker.booking.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.shared.contract.query.FindRouteCandidatesQuery;
import com.example.cargotracker.shared.contract.query.RouteCandidateDto;
import com.example.cargotracker.shared.contract.query.RouteCandidatesResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 経路候補の ACL（US08）。契約 DTO から自 BC の型への変換と、失敗の伝え方を見る。 */
class QueryBusRouteCandidateFinderTest {

    private static final Instant LOAD = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant UNLOAD = Instant.parse("2026-09-24T18:00:00Z");

    private static RouteSearchRequest request() {
        return new RouteSearchRequest(Location.of("JPTYO"), Location.of("USNYC"),
                LocalDate.of(2026, Month.DECEMBER, 1), CargoType.HAZARDOUS,
                List.of(Location.of("SGSIN")), Location.of("NLRTM"));
    }

    /** 送られたクエリを覚えて、決めた応答を返す送り口。 */
    private static QueryDispatcher dispatcherReturning(Object response, Object[] captured) {
        return new QueryDispatcher(new QueryDispatcher.Gateway() {
            @Override
            public <R> CompletableFuture<R> query(Object query, Class<R> responseType) {
                captured[0] = query;
                return CompletableFuture.completedFuture(responseType.cast(response));
            }
        });
    }

    private static QueryDispatcher dispatcherFailingWith(RuntimeException failure) {
        return new QueryDispatcher(new QueryDispatcher.Gateway() {
            @Override
            public <R> CompletableFuture<R> query(Object query, Class<R> responseType) {
                return CompletableFuture.failedFuture(failure);
            }
        });
    }

    @Test
    @DisplayName("条件をすべて載せて問い合わせる（貨物種別・除外港・起点も）")
    void sendsEveryCondition() {
        Object[] captured = new Object[1];
        new QueryBusRouteCandidateFinder(dispatcherReturning(
                new RouteCandidatesResponse(List.of(), false), captured)).find(request());

        FindRouteCandidatesQuery sent = (FindRouteCandidatesQuery) captured[0];
        assertThat(sent.originUnLocode()).isEqualTo("JPTYO");
        assertThat(sent.destinationUnLocode()).isEqualTo("USNYC");
        assertThat(sent.arrivalDeadline()).isEqualTo(LocalDate.of(2026, Month.DECEMBER, 1));
        // 種別を落とすと、危険物を運べない航海が候補に混ざる。
        assertThat(sent.cargoType()).isEqualTo("HAZARDOUS");
        assertThat(sent.excludeUnLocodes()).containsExactly("SGSIN");
        assertThat(sent.departFromUnLocode()).isEqualTo("NLRTM");
    }

    @Test
    @DisplayName("契約 DTO を自 BC の型へ組み直す")
    void convertsContractDtoToOwnTypes() {
        RouteCandidatesResponse response = new RouteCandidatesResponse(
                List.of(new RouteCandidateDto(
                        List.of(new RouteCandidateDto.LegDto("V-MOL-001", "JPTYO", "USNYC",
                                LOAD, UNLOAD)),
                        14, true)),
                true);

        RouteCandidateFinder.RouteCandidates found =
                new QueryBusRouteCandidateFinder(dispatcherReturning(response, new Object[1]))
                        .find(request());

        assertThat(found.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.direct()).isTrue();
            assertThat(candidate.transitDays()).isEqualTo(14);
            assertThat(candidate.legs()).singleElement().satisfies(leg -> {
                assertThat(leg.voyageNumber()).isEqualTo("V-MOL-001");
                assertThat(leg.load()).isEqualTo(Location.of("JPTYO"));
                assertThat(leg.unload()).isEqualTo(Location.of("USNYC"));
            });
        });
        // 打ち切りを落とすと、上限まで探したことが画面に届かない。
        assertThat(found.truncated()).isTrue();
    }

    @Test
    @DisplayName("候補 0 件はそのまま返す（失敗にしない）")
    void emptyIsNotAFailure() {
        RouteCandidateFinder.RouteCandidates found = new QueryBusRouteCandidateFinder(
                dispatcherReturning(new RouteCandidatesResponse(List.of(), false), new Object[1]))
                .find(request());

        assertThat(found.candidates()).isEmpty();
        assertThat(found.truncated()).isFalse();
    }

    @Test
    @DisplayName("問い合わせられないときは 0 件にしない（503 の元になる例外を投げる）")
    void unavailableIsNotEmpty() {
        // 空リストにすると「候補が無い」と読まれ、条件を変え続けることになる。
        assertThatThrownBy(() -> new QueryBusRouteCandidateFinder(
                dispatcherFailingWith(new IllegalStateException("no handler for query")))
                .find(request()))
                .isInstanceOf(RouteCandidateFinder.RouteSearchUnavailable.class);
    }

    @Test
    @DisplayName("経路設計側の業務の断りは、そのまま業務の断りとして通す")
    void businessRejectionIsNotTurnedIntoAnOutage() {
        // 「知らない港」を 503 にすると、直せる入力の誤りが「あとで試して」に化ける。
        assertThatThrownBy(() -> new QueryBusRouteCandidateFinder(
                dispatcherFailingWith(new BusinessRuleViolation("知らない貨物種別です: X")))
                .find(request()))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("起点を指定しないときは departFrom を空にして送る")
    void omitsDepartFromWhenNotGiven() {
        Object[] captured = new Object[1];
        var finder = new QueryBusRouteCandidateFinder(
                dispatcherReturning(new RouteCandidatesResponse(List.of(), false), captured));

        finder.find(new RouteSearchRequest(Location.of("JPTYO"), Location.of("USNYC"),
                LocalDate.of(2026, Month.DECEMBER, 1), CargoType.GENERAL, List.of(), null));

        assertThat(((FindRouteCandidatesQuery) captured[0]).departFromUnLocode()).isNull();
    }

    @Test
    @DisplayName("応答が無いのは 0 件ではない（空リストにしない）")
    void nullResponseIsNotEmpty() {
        Object[] captured = new Object[1];
        var finder = new QueryBusRouteCandidateFinder(dispatcherReturning(null, captured));

        assertThatThrownBy(() -> finder.find(request()))
                .isInstanceOf(RouteCandidateFinder.RouteSearchUnavailable.class);
    }
}
