package com.example.cargotracker.routing;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoyageScheduleSearchService 単体テスト")
class VoyageScheduleSearchServiceTest {

    @Mock
    private VoyageQueryPort voyageRepository;

    private VoyageScheduleSearchService service;

    private Voyage sg001; // JPTYO→SGSIN 到着 2026-06-15
    private Voyage sg002; // JPTYO→KRPUS→SGSIN 到着 2026-06-19（経由便）
    private Voyage sg003; // JPTYO→SGSIN 到着 2026-06-28

    @BeforeEach
    void setUp() {
        service = new VoyageScheduleSearchService(voyageRepository);

        sg001 = new Voyage("SG001", "Japan Pacific Lines", Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
            List.of(new VoyageLeg("JPTYO", "SGSIN", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15))));

        sg002 = new Voyage("SG002", "Korea Shipping Corp", Set.of(CargoType.GENERAL, CargoType.HAZARDOUS),
            List.of(
                new VoyageLeg("JPTYO", "KRPUS", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)),
                new VoyageLeg("KRPUS", "SGSIN", LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 19))
            ));

        sg003 = new Voyage("SG003", "Cold Chain Carriers", Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
            List.of(new VoyageLeg("JPTYO", "SGSIN", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 28))));
    }

    @Test
    @DisplayName("出発地・目的地・期限で期限内の航海を返す")
    void search_期限内() {
        when(voyageRepository.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(sg001, sg002, sg003));

        // 期限 2026-06-20: SG001(着6/15), SG002(着6/19) が該当。SG003(着6/28) は除外
        List<Voyage> results = service.search("JPTYO", "SGSIN", LocalDate.of(2026, 6, 20));

        assertThat(results).containsExactlyInAnyOrder(sg001, sg002);
    }

    @Test
    @DisplayName("期限が null の場合は全航海を返す")
    void search_期限なし() {
        when(voyageRepository.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(sg001, sg002, sg003));

        List<Voyage> results = service.search("JPTYO", "SGSIN", null);

        assertThat(results).containsExactlyInAnyOrder(sg001, sg002, sg003);
    }

    @Test
    @DisplayName("該当する航海がない場合は空リストを返す")
    void search_空リスト() {
        when(voyageRepository.searchVoyages("ZZZXX", "YYYYY")).thenReturn(List.of());

        List<Voyage> results = service.search("ZZZXX", "YYYYY", null);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("期限ちょうど（到着日 == 期限）の航海は含まれる")
    void search_期限ちょうど() {
        when(voyageRepository.searchVoyages("JPTYO", "SGSIN"))
            .thenReturn(List.of(sg001, sg003));

        // 期限 2026-06-28: SG003(着6/28) は期限ちょうどなので含まれる
        List<Voyage> results = service.search("JPTYO", "SGSIN", LocalDate.of(2026, 6, 28));

        assertThat(results).containsExactlyInAnyOrder(sg001, sg003);
    }
}
