package com.example.cargotracker.routing.domain.model;

import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 航海集約ルート。
 * VoyageNumber（値オブジェクト）と Schedule（値オブジェクト）を保持する。
 */
public class Voyage {

    private final Long id;
    private final VoyageNumber voyageNumber;
    private final Schedule schedule;

    public Voyage(VoyageNumber voyageNumber, Schedule schedule) {
        this(null, voyageNumber, schedule);
    }

    public Voyage(Long id, VoyageNumber voyageNumber, Schedule schedule) {
        this.id = id;
        this.voyageNumber = Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
    }

    public Long getId() {
        return id;
    }

    public VoyageNumber getVoyageNumber() {
        return voyageNumber;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    /**
     * 最初の出発地（Schedule の先頭 CarrierMovement の出発地）を返す。
     */
    public Location getDepartureLocation() {
        if (schedule.isEmpty()) return null;
        return schedule.carrierMovements().get(0).getDepartureLocation();
    }

    /**
     * 最後の到着地（Schedule の末尾 CarrierMovement の到着地）を返す。
     */
    public Location getArrivalLocation() {
        if (schedule.isEmpty()) return null;
        var movements = schedule.carrierMovements();
        return movements.get(movements.size() - 1).getArrivalLocation();
    }

    /**
     * 最初の出発日時を返す。
     */
    public LocalDateTime getDepartureTime() {
        if (schedule.isEmpty()) return null;
        return schedule.carrierMovements().get(0).getDepartureTime();
    }

    /**
     * 最後の到着日時を返す。
     */
    public LocalDateTime getArrivalTime() {
        if (schedule.isEmpty()) return null;
        var movements = schedule.carrierMovements();
        return movements.get(movements.size() - 1).getArrivalTime();
    }

    /**
     * 出発から到着までの所要日数を返す。
     */
    public long getDurationDays() {
        LocalDateTime dep = getDepartureTime();
        LocalDateTime arr = getArrivalTime();
        if (dep == null || arr == null) return 0;
        return ChronoUnit.DAYS.between(dep.toLocalDate(), arr.toLocalDate());
    }

    /**
     * 全運送区間の基本運賃合計を返す（円）。
     */
    public int getTotalBaseFare() {
        return schedule.carrierMovements().stream()
                .mapToInt(CarrierMovement::getBaseFareAmount)
                .sum();
    }
}
