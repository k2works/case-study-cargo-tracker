package com.example.routingms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("航海スケジュール検索（ユースケース）")
class SearchVoyageUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Voyage STORED = Voyage.register(VoyageNumber.of("V0100"), "さくら丸", "日本郵船",
            Set.of(CargoType.GENERAL),
            Schedule.of(List.of(CarrierMovement.of(TOKYO, LOS_ANGELES,
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-18T12:00:00Z")))));

    private final RecordingVoyageRepository voyages = new RecordingVoyageRepository();
    private final SearchVoyageUseCase useCase = new SearchVoyageUseCase(voyages);

    @Test
    @DisplayName("検索条件と既定上限を repository に渡し、総件数と上限を返す")
    void searchesWithTheDefaultLimit() {
        VoyageSearchCriteria criteria = new VoyageSearchCriteria(
                "JPTYO", "USLAX", Instant.parse("2026-10-01T00:00:00Z"),
                null, CargoType.GENERAL);
        voyages.searchResult = List.of(STORED);
        voyages.totalCount = 3;

        SearchVoyageUseCase.Result result = useCase.search(criteria);

        assertThat(voyages.lastCriteria).isEqualTo(criteria);
        assertThat(voyages.lastLimit).isEqualTo(SearchVoyageUseCase.DEFAULT_LIMIT);
        assertThat(result.voyages()).containsExactly(STORED);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(SearchVoyageUseCase.DEFAULT_LIMIT);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @DisplayName("総件数が返却件数以下なら上限で切っていない")
    void reportsWhenTheResultIsNotTruncated() {
        voyages.searchResult = List.of(STORED);
        voyages.totalCount = 1;

        SearchVoyageUseCase.Result result = useCase.search(VoyageSearchCriteria.all());

        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("航海番号で 1 件を取り出す")
    void findsByVoyageNumber() {
        voyages.found = Optional.of(STORED);

        assertThat(useCase.findByNumber(VoyageNumber.of("V0100"))).contains(STORED);
        assertThat(voyages.lastVoyageNumber).isEqualTo(VoyageNumber.of("V0100"));
    }

    private static final class RecordingVoyageRepository implements VoyageRepository {

        private VoyageSearchCriteria lastCriteria;
        private int lastLimit;
        private VoyageNumber lastVoyageNumber;
        private List<Voyage> searchResult = List.of();
        private int totalCount;
        private Optional<Voyage> found = Optional.empty();

        @Override
        public Voyage save(Voyage voyage) {
            return voyage;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
            lastVoyageNumber = voyageNumber;
            return found;
        }

        @Override
        public List<Voyage> search(VoyageSearchCriteria criteria, int limit) {
            lastCriteria = criteria;
            lastLimit = limit;
            return searchResult;
        }

        @Override
        public int countMatching(VoyageSearchCriteria criteria) {
            lastCriteria = criteria;
            return totalCount;
        }

        @Override
        public List<Voyage> findCandidates(RouteSearchSpecification specification,
                Instant notDepartedBefore) {
            return List.of();
        }
    }
}
