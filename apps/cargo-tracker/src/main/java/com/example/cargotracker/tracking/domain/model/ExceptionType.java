package com.example.cargotracker.tracking.domain.model;

import java.util.List;

/**
 * 例外の種別（US19 / US20）。
 *
 * <p><strong>エスカレーションが要るかどうかは種別が持つ。</strong> 画面や
 * アプリケーションサービスで「紛失なら」と書くと、規則が実装のあちこちに散る。
 * <strong>種別を 1 つ足したときに、どこを直せばよいかが分からなくなる。</strong>
 *
 * <p>表示名は利用者に見せることばである。列挙子名（{@code LOST}）は見せない。
 */
public enum ExceptionType {

    /** 遅延（US19）。到着が予定より遅れる。 */
    DELAY("遅延", false),

    /**
     * 破損（US20）。
     *
     * <p><strong>エスカレーションはしない。</strong> 破損は程度に幅があり、
     * 現場の判断で処理できるものが多い。すべてを管理職に上げると、
     * <strong>本当に上げるべき紛失が埋もれる</strong>。
     */
    DAMAGE("破損", false),

    /**
     * 紛失（US20）。
     *
     * <p><strong>必ずエスカレーションする。</strong> 貨物そのものが失われており、
     * 保険手続き・補償対応は現場の権限を超える。US20 の受入基準が
     * 「緊急フラグが設定されて管理職への escalation 通知が送信される」と定めている。
     */
    LOST("紛失", true),

    /**
     * 税関保留（US29 / IT11）。
     *
     * <p><strong>本 IT では画面から選べない。</strong> {@code domain-model.md} の
     * ビジネスルール 4 は「税関システムから自動登録」と書くが、
     * ADR-006 により外部システムとは連携しない。どう起票するかは US29 で決める。
     */
    CUSTOMS_HOLD("税関保留", false),

    /**
     * 誤配（US28）。
     *
     * <p><strong>画面からは登録できない。</strong> 誤配は荷役の登録で検知されるもので
     * あり、追跡管理者が手で起票するものではない。手で起票できると、
     * <strong>荷役の記録が無いのに誤配の例外だけがある</strong>状態を作れてしまう。
     *
     * <p>名前は既存の {@code CargoRoutingStatus.MISROUTED} /
     * {@code HandlingValidation.Outcome.MISROUTED} に揃える。BC が違っても、
     * 業務の言葉が違うわけではない。
     */
    MISROUTED("誤配", false);

    private final String displayName;
    private final boolean escalationRequired;

    ExceptionType(String displayName, boolean escalationRequired) {
        this.displayName = displayName;
        this.escalationRequired = escalationRequired;
    }

    /** 画面・通知に出す日本語名。 */
    public String displayName() {
        return displayName;
    }

    /** 管理職へのエスカレーションが要るか。 */
    public boolean escalationRequired() {
        return escalationRequired;
    }

    /** 追跡管理者が画面から登録できる種別（US19 / US20）。 */
    public static List<ExceptionType> manuallyRaisable() {
        return List.of(DELAY, DAMAGE, LOST);
    }
}
