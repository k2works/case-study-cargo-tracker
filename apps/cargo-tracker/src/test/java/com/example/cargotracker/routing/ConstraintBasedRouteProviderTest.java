package com.example.cargotracker.routing;

import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import com.example.cargotracker.routing.domain.services.CargoTypeConstraint;
import com.example.cargotracker.routing.domain.services.CompositeRouteConstraintChecker;
import com.example.cargotracker.routing.domain.services.DeadlineConstraint;
import com.example.cargotracker.routing.infrastructure.adapters.ConstraintBasedRouteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConstraintBasedRouteProvider テスト")
class ConstraintBasedRouteProviderTest {

    @Mock
    private VoyageQueryPort voyageQueryPort;

    private ConstraintBasedRouteProvider provider;

    private static final Voyage VOYAGE_SG001 = new Voyage(
        "SG001", "Japan Pacific Lines",
        Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
    );

    private static final Voyage VOYAGE_SG002 = new Voyage(
        "SG002", "Korea Shipping Corp",
        Set.of(CargoType.GENERAL, CargoType.HAZARDOUS),
        List.of(
            new VoyageLeg("JPTYO", "KRPUS", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)),
            new VoyageLeg("KRPUS", "SGSIN", LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 19))
        )
    );

    private static final Voyage VOYAGE_SG003_LATE = new Voyage(
        "SG003", "Cold Chain Carriers",
        Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 28)))
    );

    @BeforeEach
    void setUp() {
        var checker = new CompositeRouteConstraintChecker(
            new DeadlineConstraint(), new CargoTypeConstraint()
        );
        provider = new ConstraintBasedRouteProvider(voyageQueryPort, checker);
    }

    @Test
    @DisplayName("制約を満たす航海をルート候補に変換して返す")
    void findRoutes_制約を満たす候補を返す() {
        var query = new RouteSearchQuery(
            "JPTYO", "SGSIN", LocalDate.of(2026, 6, 20),
            CargoType.GENERAL, new BigDecimal("100")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(VOYAGE_SG001, VOYAGE_SG002, VOYAGE_SG003_LATE));

        List<RouteCandidate> result = provider.findRoutes(query);

        // SG003_LATE は期限超過（2026-06-28 > 2026-06-20）なので除外される
        assertThat(result).hasSize(2)
            .extracting(RouteCandidate::voyageNumber)
            .containsExactlyInAnyOrder("SG001", "SG002");
    }

    @Test
    @DisplayName("貨物種別が非対応の航海は除外される")
    void findRoutes_貨物種別非対応は除外() {
        var query = new RouteSearchQuery(
            "JPTYO", "SGSIN", LocalDate.of(2026, 6, 25),
            CargoType.REFRIGERATED, new BigDecimal("100")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(VOYAGE_SG001, VOYAGE_SG002));

        List<RouteCandidate> result = provider.findRoutes(query);

        // SG002 は HAZARDOUS のみ対応（REFRIGERATED 非対応）→除外
        assertThat(result).hasSize(1)
            .extracting(RouteCandidate::voyageNumber)
            .containsExactly("SG001");
    }

    @Test
    @DisplayName("ルート候補の estimatedArrival は最終レグの到着日")
    void findRoutes_estimatedArrivalは最終レグ() {
        var query = new RouteSearchQuery(
            "JPTYO", "SGSIN", LocalDate.of(2026, 6, 25),
            CargoType.GENERAL, new BigDecimal("100")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(VOYAGE_SG002));

        List<RouteCandidate> result = provider.findRoutes(query);

        // SG002 の最終レグ到着日: 2026-06-19
        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedArrival()).isEqualTo(LocalDate.of(2026, 6, 19));
    }

    @Test
    @DisplayName("viaLocodes は経路上の全ロケードを含む")
    void findRoutes_viaLocodesに全経路が含まれる() {
        var query = new RouteSearchQuery(
            "JPTYO", "SGSIN", LocalDate.of(2026, 6, 25),
            CargoType.GENERAL, new BigDecimal("100")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(VOYAGE_SG002));

        List<RouteCandidate> result = provider.findRoutes(query);

        // SG002: JPTYO → KRPUS → SGSIN
        assertThat(result.get(0).viaLocodes())
            .containsExactly("JPTYO", "KRPUS", "SGSIN");
    }

    @Test
    @DisplayName("ルート候補の estimatedPrice は正の値")
    void findRoutes_estimatedPriceは正の値() {
        var query = new RouteSearchQuery(
            "JPTYO", "SGSIN", LocalDate.of(2026, 6, 20),
            CargoType.GENERAL, new BigDecimal("1000")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(VOYAGE_SG001));

        List<RouteCandidate> result = provider.findRoutes(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedPrice()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("該当航海なしのとき空リストを返す")
    void findRoutes_該当なし() {
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2026, 6, 20),
            CargoType.GENERAL, new BigDecimal("100")
        );
        when(voyageQueryPort.searchVoyages("JPTYO", "USNYC"))
            .thenReturn(List.of());

        List<RouteCandidate> result = provider.findRoutes(query);

        assertThat(result).isEmpty();
    }
}
