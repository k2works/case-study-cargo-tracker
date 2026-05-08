package com.example.routingms.application.service;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.entities.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Itinerary;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.domain.ports.VoyageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFinderServiceTest {

    @Test
    void 直行便を優先し所要日数順で返す() {
        RouteFinderService service = new RouteFinderService(new InMemoryVoyageRepository(List.of(
                voyage("V001", movement("JPTYO", "CNSHA", 1, 4, 1, 10)),
                voyage("V002", movement("JPTYO", "HKHKG", 1, 2, 1, 4)),
                voyage("V003", movement("HKHKG", "CNSHA", 1, 5, 1, 8))
        )));

        List<Itinerary> itineraries = service.findItineraries("JPTYO", "CNSHA", LocalDate.of(2026, 1, 31));

        assertThat(itineraries).hasSize(2);
        assertThat(itineraries.get(0).getLegs()).hasSize(1);
        assertThat(itineraries.get(0).getDurationDays()).isEqualTo(6);
        assertThat(itineraries.get(1).getLegs()).hasSize(2);
    }

    @Test
    void 到着期限を超える旅程は除外する() {
        RouteFinderService service = new RouteFinderService(new InMemoryVoyageRepository(List.of(
                voyage("V001", movement("JPTYO", "CNSHA", 1, 4, 1, 10)),
                voyage("V002", movement("JPTYO", "CNSHA", 1, 20, 1, 25))
        )));

        List<Itinerary> itineraries = service.findItineraries("JPTYO", "CNSHA", LocalDate.of(2026, 1, 15));

        assertThat(itineraries).hasSize(1);
        assertThat(itineraries.get(0).getFinalUnloadTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    void 乗り継ぎ時は前区間到着後に後区間が出発する必要がある() {
        RouteFinderService service = new RouteFinderService(new InMemoryVoyageRepository(List.of(
                voyage("V001", movement("JPTYO", "HKHKG", 1, 2, 1, 4)),
                voyage("V002", movement("HKHKG", "CNSHA", 1, 3, 1, 5)),
                voyage("V003", movement("HKHKG", "CNSHA", 1, 5, 1, 7))
        )));

        List<Itinerary> itineraries = service.findItineraries("JPTYO", "CNSHA", LocalDate.of(2026, 1, 31));

        assertThat(itineraries).hasSize(1);
        assertThat(itineraries.get(0).getLegs()).hasSize(2);
        assertThat(itineraries.get(0).getLegs().get(1).getVoyageNumber()).isEqualTo("V003");
    }

    @Test
    void 出発地に戻る区間は乗り継ぎ候補にしない() {
        RouteFinderService service = new RouteFinderService(new InMemoryVoyageRepository(List.of(
                voyage("V001", movement("JPTYO", "JPTYO", 1, 2, 1, 3)),
                voyage("V002", movement("JPTYO", "CNSHA", 1, 4, 1, 5))
        )));

        List<Itinerary> itineraries = service.findItineraries("JPTYO", "CNSHA", null);

        assertThat(itineraries).hasSize(1);
        assertThat(itineraries.get(0).getLegs()).hasSize(1);
    }

    @Test
    void 乗り継ぎ便の到着地や期限が合わなければ除外する() {
        RouteFinderService service = new RouteFinderService(new InMemoryVoyageRepository(List.of(
                voyage("V001", movement("JPTYO", "HKHKG", 1, 2, 1, 4)),
                voyage("V002", movement("HKHKG", "USNYC", 1, 5, 1, 7)),
                voyage("V003", movement("HKHKG", "CNSHA", 1, 5, 2, 10))
        )));

        List<Itinerary> itineraries = service.findItineraries("JPTYO", "CNSHA", LocalDate.of(2026, 1, 31));

        assertThat(itineraries).isEmpty();
    }

    private static Voyage voyage(String voyageNumber, CarrierMovement... movements) {
        return new Voyage(new VoyageNumber(voyageNumber), new Schedule(List.of(movements)));
    }

    private static CarrierMovement movement(String from, String to, int departureMonth, int departureDay,
                                            int arrivalMonth, int arrivalDay) {
        return new CarrierMovement(
                from,
                to,
                ZonedDateTime.of(2026, departureMonth, departureDay, 10, 0, 0, 0, ZoneOffset.UTC),
                ZonedDateTime.of(2026, arrivalMonth, arrivalDay, 10, 0, 0, 0, ZoneOffset.UTC),
                1
        );
    }

    private static final class InMemoryVoyageRepository implements VoyageRepository {
        private final List<Voyage> voyages;

        private InMemoryVoyageRepository(List<Voyage> voyages) {
            this.voyages = voyages;
        }

        @Override
        public Voyage save(Voyage voyage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Voyage voyage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
            return voyages.stream().filter(v -> v.getVoyageNumber().equals(voyageNumber)).findFirst();
        }

        @Override
        public List<Voyage> findAll() {
            return voyages;
        }

        @Override
        public void deleteByVoyageNumber(VoyageNumber voyageNumber) {
            throw new UnsupportedOperationException();
        }
    }
}
