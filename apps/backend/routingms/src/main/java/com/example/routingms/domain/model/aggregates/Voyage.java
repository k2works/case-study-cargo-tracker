package com.example.routingms.domain.model.aggregates;

import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.domain.model.valueobjects.Schedule;
import java.util.Objects;

/**
 * 航海（Voyage）集約ルート
 * 航海番号とスケジュール（キャリア移動のリスト）を持つ集約
 */
public class Voyage {

    private Long id;
    private final VoyageNumber voyageNumber;
    private Schedule schedule;

    /**
     * 新規 Voyage 作成コンストラクタ（id なし）
     */
    public Voyage(VoyageNumber voyageNumber, Schedule schedule) {
        Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        this.voyageNumber = voyageNumber;
        this.schedule = schedule;
    }

    /**
     * 永続化済み Voyage 再構成コンストラクタ（id あり）
     */
    public Voyage(Long id, VoyageNumber voyageNumber, Schedule schedule) {
        this(voyageNumber, schedule);
        this.id = id;
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
     * スケジュールを更新する
     */
    public void updateSchedule(Schedule newSchedule) {
        Objects.requireNonNull(newSchedule, "newSchedule must not be null");
        this.schedule = newSchedule;
    }

    /**
     * 指定の出発地・到着地区間を含むか判定する
     */
    public boolean includesMovement(String departureUnlocode, String arrivalUnlocode) {
        return schedule.getCarrierMovements().stream()
                .anyMatch(m -> m.getDepartureLocationUnlocode().equals(departureUnlocode)
                        && m.getArrivalLocationUnlocode().equals(arrivalUnlocode));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Voyage voyage = (Voyage) o;
        return Objects.equals(voyageNumber, voyage.voyageNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voyageNumber);
    }

    @Override
    public String toString() {
        return "Voyage{voyageNumber=" + voyageNumber + '}';
    }
}
