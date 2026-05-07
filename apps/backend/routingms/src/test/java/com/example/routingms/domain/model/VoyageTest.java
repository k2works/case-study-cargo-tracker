package com.example.routingms.domain.model;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.entities.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Voyage 集約ルートテスト")
class VoyageTest {

    private static final ZonedDateTime DEP = ZonedDateTime.parse("2025-01-10T08:00:00+09:00");
    private static final ZonedDateTime ARR = ZonedDateTime.parse("2025-01-12T18:00:00+09:00");

    private Voyage createVoyage(String voyageNumber) {
        CarrierMovement cm = new CarrierMovement("JPTYO", "CNSHA", DEP, ARR, 0);
        Schedule schedule = new Schedule(List.of(cm));
        return new Voyage(new VoyageNumber(voyageNumber), schedule);
    }

    @Test
    @DisplayName("Voyage を正常に生成できること")
    void shouldCreateVoyage() {
        Voyage voyage = createVoyage("V001");
        assertThat(voyage.getVoyageNumber().getNumber()).isEqualTo("V001");
        assertThat(voyage.getSchedule().getCarrierMovements()).hasSize(1);
        assertThat(voyage.getId()).isNull();
    }

    @Test
    @DisplayName("id 付きの Voyage を生成できること（永続化後の再構成）")
    void shouldCreateVoyageWithId() {
        CarrierMovement cm = new CarrierMovement("JPTYO", "CNSHA", DEP, ARR, 0);
        Schedule schedule = new Schedule(List.of(cm));
        Voyage voyage = new Voyage(1L, new VoyageNumber("V001"), schedule);
        assertThat(voyage.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("スケジュールを更新できること")
    void shouldUpdateSchedule() {
        Voyage voyage = createVoyage("V001");
        CarrierMovement newCm = new CarrierMovement("JPOSA", "KRPUS", DEP, ARR, 0);
        Schedule newSchedule = new Schedule(List.of(newCm));
        voyage.updateSchedule(newSchedule);
        assertThat(voyage.getSchedule().getCarrierMovements().get(0).getDepartureLocationUnlocode())
                .isEqualTo("JPOSA");
    }

    @Test
    @DisplayName("指定区間を含む場合は true を返すこと")
    void shouldReturnTrueWhenIncludesMovement() {
        Voyage voyage = createVoyage("V001");
        assertThat(voyage.includesMovement("JPTYO", "CNSHA")).isTrue();
        assertThat(voyage.includesMovement("JPOSA", "KRPUS")).isFalse();
    }

    @Test
    @DisplayName("同じ航海番号の Voyage は等価であること")
    void shouldBeEqualWhenSameVoyageNumber() {
        Voyage v1 = createVoyage("V001");
        Voyage v2 = createVoyage("V001");
        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    }

    @Test
    @DisplayName("toString が航海番号を含むこと")
    void shouldHaveToString() {
        Voyage voyage = createVoyage("V001");
        assertThat(voyage.toString()).contains("V001");
    }
}
