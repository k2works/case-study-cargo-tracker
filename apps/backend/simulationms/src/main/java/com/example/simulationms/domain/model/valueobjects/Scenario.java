package com.example.simulationms.domain.model.valueobjects;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 実行するシナリオ。工程の並びそのものである。
 *
 * <p>並びを持つのは、<strong>どこまで進んだか</strong>を工程の位置で言えるようにするため。
 */
public record Scenario(String id, List<ScenarioStep> steps) {

    public Scenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("シナリオ ID は必須です");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("シナリオには少なくとも 1 つの工程が要ります");
        }
        if (new LinkedHashSet<>(steps).size() != steps.size()) {
            throw new IllegalArgumentException("同じ工程を 2 度並べることはできません: " + id);
        }
        steps = List.copyOf(steps);
    }

    public static Scenario of(String id, List<ScenarioStep> steps) {
        return new Scenario(id, steps);
    }

    /**
     * 一般貨物の標準輸送。予約から精算までを通す。
     *
     * <p><strong>例外の工程は含めない。</strong>{@code values()} をそのまま並べていた
     * IT14 の形は、工程を足した瞬間に標準輸送へ混ざる——正常系のシナリオが
     * 誤配や破損を起こすようになる。
     */
    public static Scenario standardTransport() {
        return new Scenario("standard-transport", HAPPY_PATH);
    }

    /** 追跡番号の発行までの道。例外シナリオもここまでは同じ道を通る。 */
    private static final List<ScenarioStep> UNTIL_TRACKING = List.of(
            ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING,
            ScenarioStep.REQUEST_ROUTING, ScenarioStep.REGISTER_VOYAGE,
            ScenarioStep.ASSIGN_ROUTE, ScenarioStep.NOTIFY_ROUTE,
            ScenarioStep.CONFIRM_BOOKING, ScenarioStep.ISSUE_TRACKING_NUMBER);

    /** 荷役から精算まで。例外に対応したあとも、この道に戻って精算まで通る。 */
    private static final List<ScenarioStep> HANDLING_TO_SETTLEMENT = List.of(
            ScenarioStep.RECORD_HANDLING, ScenarioStep.DECLARE_CUSTOMS,
            ScenarioStep.CLEAR_CUSTOMS, ScenarioStep.RECORD_CLAIM,
            ScenarioStep.CALCULATE_CHARGE, ScenarioStep.SETTLE);

    private static final List<ScenarioStep> HAPPY_PATH =
            concat(UNTIL_TRACKING, HANDLING_TO_SETTLEMENT);

    /**
     * 例外を含むシナリオ（US36・[ADR-031] 決定 5）。
     *
     * <p><strong>例外は起こすだけでは仕事にならない。</strong>US36-2 が見たいのは
     * 「例外が起きたあとの業務」であり、対応（解決・組み直し・承認）まで並べて
     * はじめて通したことになる。
     *
     * <p>キャンセルだけは精算まで進めない。キャンセルした貨物の引取は成り立たず、
     * 並べると必ず失敗する——落ちたのは業務ではなく並べ方である。
     */
    /**
     * 実行できるシナリオのすべて。
     *
     * <p><strong>画面が一覧を持たない。</strong>持つと、足したシナリオが実装済みなのに
     * 選べないという形になる。正常系を先頭に置くのは、実演がそこから始まるためである。
     */
    public static List<Scenario> all() {
        List<Scenario> scenarios = new java.util.ArrayList<>();
        scenarios.add(standardTransport());
        scenarios.addAll(exceptionScenarios());
        return List.copyOf(scenarios);
    }

    /**
     * 名前で引く。
     *
     * <p><strong>知らない名前は既定へ落とさない。</strong>落とすと、指示したものと違う
     * ものが流れたことに誰も気づけない。
     */
    public static java.util.Optional<Scenario> findById(String id) {
        return all().stream().filter(scenario -> scenario.id().equals(id)).findFirst();
    }

    public static List<Scenario> exceptionScenarios() {
        return List.of(
                // 予定より遅い日時で荷役を記録し、起きた遅延を解決してから先へ進む。
                new Scenario("delay", concat(UNTIL_TRACKING,
                        List.of(ScenarioStep.RECORD_LATE_HANDLING,
                                ScenarioStep.RESOLVE_EXCEPTION),
                        HANDLING_TO_SETTLEMENT)),
                // 荷役中に破損に気づいた人が起票し、追跡管理者が解決する。
                new Scenario("damage", concat(UNTIL_TRACKING,
                        List.of(ScenarioStep.RECORD_HANDLING, ScenarioStep.RAISE_DAMAGE,
                                ScenarioStep.RESOLVE_EXCEPTION),
                        List.of(ScenarioStep.DECLARE_CUSTOMS, ScenarioStep.CLEAR_CUSTOMS,
                                ScenarioStep.RECORD_CLAIM, ScenarioStep.CALCULATE_CHARGE,
                                ScenarioStep.SETTLE))),
                // 予定と違う港での荷役から誤配が検知され、現在地から組み直して再開する。
                new Scenario("misroute", concat(UNTIL_TRACKING,
                        List.of(ScenarioStep.RECORD_MISROUTED_HANDLING,
                                ScenarioStep.REDESIGN_ROUTE, ScenarioStep.RESOLVE_EXCEPTION),
                        HANDLING_TO_SETTLEMENT)),
                // 通関が保留になり、解除してから引取へ進む。
                new Scenario("customs-hold", concat(UNTIL_TRACKING,
                        List.of(ScenarioStep.RECORD_HANDLING, ScenarioStep.DECLARE_CUSTOMS,
                                ScenarioStep.HOLD_CUSTOMS, ScenarioStep.RELEASE_CUSTOMS,
                                ScenarioStep.CLEAR_CUSTOMS, ScenarioStep.RECORD_CLAIM,
                                ScenarioStep.CALCULATE_CHARGE, ScenarioStep.SETTLE))),
                // 輸送中にキャンセルを申請し、追跡管理者が承認する。ここで終わる。
                new Scenario("cancellation", concat(UNTIL_TRACKING,
                        List.of(ScenarioStep.RECORD_HANDLING,
                                ScenarioStep.REQUEST_CANCELLATION,
                                ScenarioStep.APPROVE_CANCELLATION))));
    }

    @SafeVarargs
    private static List<ScenarioStep> concat(List<ScenarioStep>... parts) {
        List<ScenarioStep> steps = new java.util.ArrayList<>();
        for (List<ScenarioStep> part : parts) {
            steps.addAll(part);
        }
        return List.copyOf(steps);
    }

    public boolean includes(ScenarioStep step) {
        return steps.contains(step);
    }

    public ScenarioStep last() {
        return steps.getLast();
    }
}
