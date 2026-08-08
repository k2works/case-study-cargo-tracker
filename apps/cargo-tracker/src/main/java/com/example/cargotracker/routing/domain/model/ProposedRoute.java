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
    private final Path path;
    private final Timing timing;
    private final Money estimatedCost;
    private final Handling handling;
    private final boolean deadlineSatisfied;
    private final int priority;

    private ProposedRoute(
            VoyageNumber voyageNumber,
            Path path,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied,
            int priority) {
        this.voyageNumber = voyageNumber;
        this.path = path;
        this.timing = timing;
        this.estimatedCost = estimatedCost;
        this.handling = handling;
        this.deadlineSatisfied = deadlineSatisfied;
        this.priority = priority;
    }

    /**
     * 航海のどの区間に乗り、どの区間で降りるか（区間の添字）。
     *
     * <p><strong>時刻の範囲ではなく添字で持つ。</strong> 「乗る区間から降りる区間まで」は
     * 本来ならば航海の並びの上の位置であり、時刻はその結果にすぎない。時刻の範囲で
     * 絞ると、同じ港を 2 度通る航海では<strong>どの周回の区間なのかを時刻から
     * 逆算していることになる</strong>（レビュー L1）。
     *
     * <p>探索が選んだ位置をそのまま持ち回れば、逆算は要らない。
     *
     * @param boardingIndex 乗る区間の添字（この区間から乗る）
     * @param landingIndex  降りる区間の添字（この区間で降りる。両端を含む）
     */
    public record LegRange(int boardingIndex, int landingIndex) {

        public LegRange {
            if (boardingIndex < 0) {
                throw new IllegalArgumentException("乗る区間の添字は 0 以上です");
            }
            if (landingIndex < boardingIndex) {
                throw new IllegalArgumentException("降りる区間は乗る区間より後です");
            }
        }

        /** 区間の数（両端を含む）。 */
        public int size() {
            return landingIndex - boardingIndex + 1;
        }
    }

    /**
     * この候補が航海のどこを通るか。
     *
     * <p>経由港と区間の添字を<strong>ひと組で持つ</strong>。別々に持つと
     * 「経由港はあるのに区間が 1 つ」という、航海の上で成り立たない組み合わせを
     * 作れてしまう（IT5 の {@code CargoRouting} と同じ理由）。
     *
     * @param transitPorts 経由港（乗る港と降りる港は含まない）
     * @param legRange     乗る区間から降りる区間まで
     */
    public record Path(List<Location> transitPorts, LegRange legRange) {

        public Path {
            transitPorts = List.copyOf(transitPorts);
            if (transitPorts.size() != legRange.size() - 1) {
                throw new IllegalArgumentException(
                        "経由港の数は区間の数より 1 つ少ない値です（経由港 %d / 区間 %d）"
                                .formatted(transitPorts.size(), legRange.size()));
            }
        }
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
     * @param capacityAvailable   この便に空きがあるか（US09 / IT5）
     */
    public record Handling(
            RoutingCargoType requestedCargoType,
            boolean hazardousAllowed,
            boolean refrigeratedAllowed,
            boolean capacityAvailable) {
    }

    /** 探索の結果として作る（表示順は後から振る）。 */
    static ProposedRoute of(
            VoyageNumber voyageNumber,
            Path path,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied) {
        return new ProposedRoute(voyageNumber, path, timing, estimatedCost,
                handling, deadlineSatisfied, 0);
    }

    /** 永続化された状態から復元する。 */
    public static ProposedRoute reconstruct(
            VoyageNumber voyageNumber,
            Path path,
            Timing timing,
            Money estimatedCost,
            Handling handling,
            boolean deadlineSatisfied,
            int priority) {
        return new ProposedRoute(voyageNumber, path, timing, estimatedCost,
                handling, deadlineSatisfied, priority);
    }

    /** 表示順を振った複製。 */
    ProposedRoute withPriority(int newPriority) {
        return new ProposedRoute(voyageNumber, path, timing, estimatedCost,
                handling, deadlineSatisfied, newPriority);
    }

    /**
     * この候補を選べるか。
     *
     * <p>判断するのは<strong>空き容量と取扱可否</strong>である。期限超過は警告であって
     * 禁止ではない（期限を延ばして使う判断は経路設計者がする）。
     */
    public boolean selectable() {
        return unselectableReason() == null;
    }

    /** 選べない理由。選べるなら {@code null}。 */
    public String unselectableReason() {
        // **空きが無ければ、運べても積めない。** 取扱可否より先に見る
        if (!handling.capacityAvailable()) {
            return "この便は空きがありません";
        }
        return switch (handling.requestedCargoType()) {
            case HAZARDOUS -> handling.hazardousAllowed() ? null : "この便は危険物を扱えません";
            case REFRIGERATED ->
                    handling.refrigeratedAllowed() ? null : "この便は冷凍・冷蔵を扱えません";
            case GENERAL -> null;
        };
    }

    /** 直行便か。経由が無ければ直行である。 */
    public boolean isDirect() {
        return path.transitPorts().isEmpty();
    }

    public VoyageNumber voyageNumber() {
        return voyageNumber;
    }

    public List<Location> transitPorts() {
        return path.transitPorts();
    }

    /** 乗る区間から降りる区間まで（航海の区間の添字）。 */
    public LegRange legRange() {
        return path.legRange();
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

    public boolean capacityAvailable() {
        return handling.capacityAvailable();
    }

    public boolean deadlineSatisfied() {
        return deadlineSatisfied;
    }

    public int priority() {
        return priority;
    }
}
