package com.example.routingms.domain.model.valueobjects;

import com.example.shared.domain.model.Location;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 経路候補。出発地から目的地まで、実際に運べる区間のつながり（US08）。
 *
 * <p>Booking Context の {@code CargoItinerary}（旅程）・{@code RouteCandidate}（ルート候補）とは
 * <strong>別の型</strong>である。あちらは予約・見積に紐づいて永続化されるが、こちらは
 * 都度算出して捨てる探索結果である（[ADR-017]）。変換は US09 の ACL で行う。
 *
 * <p><strong>値オブジェクトとして丸ごと比べられる。</strong>候補の比較を項目ごとに積み上げると、
 * 属性が増えるたび同じ比較漏れが起きる（IT3 の航海差分がそうだった）。
 */
public final class TransitPath {

    /**
     * 同じ港での積み替えに要する最低時間。
     *
     * <p>降ろして、税関の外で受け渡して、積む。これを 0 にすると、机上では成立するが現場で
     * 実行できない経路を候補に出す。経路設計者はスケジュール表からそれを見抜けず、動かない
     * 予定が下流（荷役・追跡）へ流れる。
     *
     * <p>6 時間は業務上の判断であり、港ごとの実態を持っていない（港湾制約のモデルは
     * 持たないと決めた。[ADR-018]）。港ごとに変える必要が出たら、そのとき港のマスタに持たせる。
     */
    public static final Duration MINIMUM_TRANSSHIPMENT = Duration.ofHours(6);

    private final List<TransitEdge> edges;

    private TransitPath(List<TransitEdge> edges) {
        this.edges = List.copyOf(edges);
    }

    /** 新規に組み立てる。ここでだけ検査する。 */
    public static TransitPath of(List<TransitEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("経路には少なくとも 1 つの区間が必要です");
        }
        for (int i = 1; i < edges.size(); i++) {
            TransitEdge previous = edges.get(i - 1);
            TransitEdge current = edges.get(i);
            if (!previous.to().equals(current.from())) {
                throw new IllegalArgumentException(
                        "区間がつながっていません。前の区間の到着地から次の区間が出発するようにしてください");
            }
            Duration layover = Duration.between(previous.arrivalTime(), current.departureTime());
            if (layover.compareTo(MINIMUM_TRANSSHIPMENT) < 0) {
                throw new IllegalArgumentException(
                        "積み替えに要する時間（%d 時間）を満たしていません".formatted(
                                MINIMUM_TRANSSHIPMENT.toHours()));
            }
        }
        return new TransitPath(edges);
    }

    public List<TransitEdge> edges() {
        return edges;
    }

    public Location origin() {
        return edges.get(0).from();
    }

    public Location destination() {
        return edges.get(edges.size() - 1).to();
    }

    public Instant departureTime() {
        return edges.get(0).departureTime();
    }

    public Instant arrivalTime() {
        return edges.get(edges.size() - 1).arrivalTime();
    }

    /**
     * 輸送日数。荷主が待つ日数であり、区間の移動時間の合計ではない。
     *
     * <p>積み替えの待ち時間も荷主にとっては待ち時間である。区間だけを足すと、乗り継ぎの
     * 多い経路が実際より短く見え、推奨順が狂う。
     */
    public int transitDays() {
        return (int) ChronoUnit.DAYS.between(departureTime(), arrivalTime());
    }

    /** 経由港。途中で乗り継ぐ港だけで、出発地と目的地は含まない。 */
    public List<Location> transitPorts() {
        List<Location> ports = new ArrayList<>();
        for (int i = 1; i < edges.size(); i++) {
            ports.add(edges.get(i).from());
        }
        return List.copyOf(ports);
    }

    /**
     * 積み替え港での待ち時間。
     *
     * <p>所要日数の合計だけでは、どこでどれだけ止まるのかが分からない。「釜山で 1 日半待つ」は
     * 候補を選ぶときの判断材料になる（US09）。
     */
    public List<Layover> layovers() {
        List<Layover> layovers = new ArrayList<>();
        for (int i = 1; i < edges.size(); i++) {
            layovers.add(new Layover(
                    edges.get(i).from(),
                    Duration.between(edges.get(i - 1).arrivalTime(), edges.get(i).departureTime())));
        }
        return List.copyOf(layovers);
    }

    /** 積み替え港での待ち時間。どの港でどれだけ待つか。 */
    public record Layover(Location port, Duration duration) {
    }

    public int transshipmentCount() {
        return edges.size() - 1;
    }

    /** 直行便か。US08 の受入基準は直行便を最優先の候補として提示することを求める。 */
    public boolean isDirect() {
        return transshipmentCount() == 0;
    }

    /** この経路で使う航海番号を、運ぶ順に返す。 */
    public List<VoyageNumber> voyageNumbers() {
        return edges.stream().map(TransitEdge::voyageNumber).toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TransitPath path && edges.equals(path.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edges);
    }

    @Override
    public String toString() {
        return edges.toString();
    }
}
