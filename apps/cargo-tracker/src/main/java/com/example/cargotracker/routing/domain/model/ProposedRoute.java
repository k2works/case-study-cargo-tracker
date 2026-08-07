package com.example.cargotracker.routing.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;

/**
 * 経路候補 1 件（US08）。
 *
 * <p><strong>選べない候補も残す</strong>（{@code domain-model.md} ビジネスルール 6）。
 * 一覧から消すと「なぜあの便が出てこないのか」を利用者が確認できなくなり、
 * 存在しない便を探し続けることになる。選べない理由は候補自身が持つ。
 */
public final class ProposedRoute {

    private final VoyageNumber voyageNumber;
    private final List<Location> transitPorts;
    private final Timing timing;
    private final Money estimatedCost;
    private final Handling handling;
    private final boolean deadlineSatisfied;
    private final int priority;

    private ProposedRoute(
            VoyageNumber voyageNumber,
            List<Location> transitPorts,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied,
            int priority) {
        this.voyageNumber = voyageNumber;
        this.transitPorts = List.copyOf(transitPorts);
        this.timing = timing;
        this.estimatedCost = estimatedCost;
        this.handling = handling;
        this.deadlineSatisfied = deadlineSatisfied;
        this.priority = priority;
    }

    /**
     * 候補の時間。
     *
     * @param departureTime 出発時刻
     * @param arrivalTime   到着時刻
     * @param transitDays   所要日数
     */
    public record Timing(Instant departureTime, Instant arrivalTime, int transitDays) {
    }

    /**
     * 貨物の取扱可否。
     *
     * <p><strong>「この貨物は何か」と「この便は何を運べるか」を 1 か所に置く。</strong>
     * 選べない理由を組み立てるには両方が要る。
     *
     * @param requestedCargoType  運びたい貨物の種別
     * @param hazardousAllowed    この便が危険物を扱えるか
     * @param refrigeratedAllowed この便が冷凍・冷蔵を扱えるか
     */
    public record Handling(
            RoutingCargoType requestedCargoType,
            boolean hazardousAllowed,
            boolean refrigeratedAllowed) {
    }

    /** 探索の結果として作る（表示順は後から振る）。 */
    static ProposedRoute of(
            VoyageNumber voyageNumber,
            List<Location> transitPorts,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied) {
        return new ProposedRoute(voyageNumber, transitPorts, timing, estimatedCost,
                handling, deadlineSatisfied, 0);
    }

    /** 永続化された状態から復元する。 */
    public static ProposedRoute reconstruct(
            VoyageNumber voyageNumber,
            List<Location> transitPorts,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied,
            int priority) {
        return new ProposedRoute(voyageNumber, transitPorts, timing, estimatedCost,
                handling, deadlineSatisfied, priority);
    }

    /** 表示順を振った複製。 */
    ProposedRoute withPriority(int newPriority) {
        return new ProposedRoute(voyageNumber, transitPorts, timing, estimatedCost,
                handling, deadlineSatisfied, newPriority);
    }

    /**
     * この候補を選べるか。
     *
     * <p>判断するのは<strong>取扱可否だけ</strong>である。期限超過は警告であって
     * 禁止ではない（期限を延ばして使う判断は経路設計者がする）。
     * 空き容量は経路の確定（US09）まで判定できないため、ここでは見ない。
     */
    public boolean selectable() {
        return unselectableReason() == null;
    }

    /** 選べない理由。選べるなら {@code null}。 */
    public String unselectableReason() {
        return switch (handling.requestedCargoType()) {
            case HAZARDOUS -> handling.hazardousAllowed() ? null : "この便は危険物を扱えません";
            case REFRIGERATED ->
                    handling.refrigeratedAllowed() ? null : "この便は冷凍・冷蔵を扱えません";
            case GENERAL -> null;
        };
    }

    /** 直行便か。経由が無ければ直行である。 */
    public boolean isDirect() {
        return transitPorts.isEmpty();
    }

    public VoyageNumber voyageNumber() {
        return voyageNumber;
    }

    public List<Location> transitPorts() {
        return transitPorts;
    }

    public Instant departureTime() {
        return timing.departureTime();
    }

    public Instant arrivalTime() {
        return timing.arrivalTime();
    }

    public int transitDays() {
        return timing.transitDays();
    }

    public Money estimatedCost() {
        return estimatedCost;
    }

    public boolean hazardousAllowed() {
        return handling.hazardousAllowed();
    }

    public boolean refrigeratedAllowed() {
        return handling.refrigeratedAllowed();
    }

    public RoutingCargoType requestedCargoType() {
        return handling.requestedCargoType();
    }

    public boolean deadlineSatisfied() {
        return deadlineSatisfied;
    }

    public int priority() {
        return priority;
    }
}
