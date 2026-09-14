package com.example.cargotracker.simulation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.Arrays;
import java.util.List;

/**
 * 業務シナリオ（US33 §受入基準 1）。
 *
 * <p><b>工程の並びはシナリオが持つ。</b> 実行が並びを組み立てると、シナリオを
 * 足すたびに実行の側を直すことになる。</p>
 *
 * <p><b>名簿に無いものは通さない。</b> 「知らないシナリオ」を素通りさせると、
 * 打ち間違いが「工程 0 件で成功」として記録される——何も確かめていないのに
 * 緑になる（名簿方式の検査は載っていないものを通す、の裏返し）。</p>
 */
public enum Scenario {

    /** 一般貨物が予約から精算まで通る。**US33 の中核**。 */
    STANDARD("一般貨物の標準輸送", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE,
            StepKind.NOTIFY_SHIPPER,
            StepKind.CONFIRM_BOOKING,
            StepKind.ISSUE_TRACKING_NUMBER,
            StepKind.RECORD_HANDLING,
            StepKind.CLEAR_CUSTOMS,
            StepKind.CLAIM_CARGO,
            StepKind.CALCULATE_INVOICE,
            StepKind.ISSUE_INVOICE,
            StepKind.RECORD_PAYMENT)),

    /**
     * 期限に間に合う便が無く、経路の確定で止まる。
     *
     * <p><b>失敗する経路も 1 本要る。</b> 成功しか流せないと、「どの工程で
     * 止まったか」を出す仕組み（US34）が確かめられない。</p>
     */
    NO_ROUTE("経路候補が見つからない輸送", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE));

    private final String label;
    private final List<StepKind> steps;

    Scenario(String label, List<StepKind> steps) {
        this.label = label;
        this.steps = steps;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }

    /** 工程の並び。<b>この順に実行する</b>。 */
    public List<StepKind> steps() {
        return steps;
    }

    /**
     * 呼び名から引く。
     *
     * <p><b>知らないものは断る。</b> 素通りさせると、打ち間違いが
     * 「工程 0 件で成功」として記録される。</p>
     */
    public static Scenario of(String label) {
        return Arrays.stream(values())
                .filter(scenario -> scenario.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolation(
                        "知らないシナリオです: " + label + "。選べるのは "
                                + Arrays.stream(values()).map(Scenario::label).toList()
                                + " です"));
    }
}
