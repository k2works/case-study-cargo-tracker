package com.example.cargotracker.estimation.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積（集約ルート。US01）。
 *
 * <p>正典は {@code domain-model.md} の「7. Estimation Context」。
 *
 * <p><strong>候補は作成時の写しである。</strong> あとで作り直さない ——
 * 先週の見積と今日の見積で数字が違っては、荷主に伝えた内容を説明できない。
 */
public class Estimate {

    private final EstimateId estimateId;

    /**
     * 見積の条件。
     *
     * <p><strong>5 つの値を一列に並べない。</strong> 出発地と目的地はどちらも
     * {@code Location} であり、取り違えても<strong>コンパイルは通り、
     * 航路が逆に出るだけ</strong>である（IT17 の R6 で 20 件直した形）。
     */
    private final EstimateSpecification specification;

    private List<RouteCandidate> candidates;
    private long version;

    private Estimate(
            EstimateId estimateId,
            EstimateSpecification specification,
            List<RouteCandidate> candidates,
            long version) {
        this.estimateId = estimateId;
        this.specification = specification;
        this.candidates = List.copyOf(candidates);
        this.version = version;
    }

    /**
     * 見積を作る（ビジネスルール 1〜3・5）。
     *
     * <p>作成直後の候補は空である。<strong>候補は Routing の探索から入れる</strong>
     * （ADR-023）。
     */
    public static Estimate create(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            EstimationCargoType cargoType,
            BigDecimal weightKg) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            // **同一地点への輸送は業務として成り立たない**（ビジネスルール 2）
            throw new IllegalArgumentException(
                    "出発地と目的地が同じです: " + origin.unlocode());
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("希望到着期限は必須です");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("重量は正の値です: " + weightKg);
        }
        return new Estimate(
                EstimateId.generate(),
                new EstimateSpecification(
                        origin, destination, arrivalDeadline, cargoType, weightKg),
                List.of(), 0L);
    }

    /**
     * 永続化から復元する。
     *
     * <p><strong>復元では検査しない</strong>（`feedback` の教訓）。列が無かったころの
     * 行や、規則を足す前に作られた見積が読めなくなる。検査は新規の受け入れ時だけ行う。
     */
    public static Estimate reconstruct(
            EstimateId estimateId,
            EstimateSpecification specification,
            List<RouteCandidate> candidates,
            long version) {
        return new Estimate(
                estimateId, specification,
                candidates == null ? List.of() : candidates, version);
    }

    /**
     * ルート候補を差し替える（ADR-023）。
     *
     * <p><strong>写して持つ。</strong> 渡した後のリストを書き換えられると、
     * 画面に出す候補と保存した候補がずれる。
     */
    public void replaceCandidates(List<RouteCandidate> newCandidates) {
        this.candidates = newCandidates == null ? List.of() : List.copyOf(newCandidates);
    }

    /**
     * 保存に成功したことを反映する。
     *
     * <p><strong>呼ぶのはリポジトリだけである</strong>（IT15 の C4）。
     */
    public void markPersisted() {
        this.version++;
    }

    /** 見積 ID。 */
    public EstimateId estimateId() {
        return estimateId;
    }

    /** 見積の条件。 */
    public EstimateSpecification specification() {
        return specification;
    }

    /** 出発地。 */
    public Location origin() {
        return specification.origin();
    }

    /** 目的地。 */
    public Location destination() {
        return specification.destination();
    }

    /** 希望到着期限。 */
    public LocalDate arrivalDeadline() {
        return specification.arrivalDeadline();
    }

    /** 貨物種別。 */
    public EstimationCargoType cargoType() {
        return specification.cargoType();
    }

    /** 重量（kg）。 */
    public BigDecimal weightKg() {
        return specification.weightKg();
    }

    /** ルート候補（写し）。 */
    public List<RouteCandidate> candidates() {
        return candidates;
    }

    /** 楽観的ロック用のバージョン。 */
    public long version() {
        return version;
    }

    /**
     * 状態（ビジネスルール 7）。
     *
     * <p><strong>期限切れは保存しない。</strong> 読み出しのたびに今日と比べる
     * （ADR-019 と同じ形）。
     *
     * <p><strong>今日を受け取らない {@code status()} は作らない。</strong>
     * 引数の無い状態は「いつの状態か」を answer できず、
     * <strong>呼ぶ側が時計を持っていないことを隠す</strong>。
     *
     * @param today 業務のタイムゾーンの今日
     */
    public EstimateStatus statusAsOf(LocalDate today) {
        return EstimateStatus.asOf(specification.arrivalDeadline(), today);
    }
}
