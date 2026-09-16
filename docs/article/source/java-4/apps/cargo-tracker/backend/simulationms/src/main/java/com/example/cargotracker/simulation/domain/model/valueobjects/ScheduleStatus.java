package com.example.cargotracker.simulation.domain.model.valueobjects;

/**
 * 継続実行の稼働状態（US36 §受入基準 4）。
 *
 * <p><b>{@code STOPPING} を置く。</b>「停止すると進行中の実行は最後まで終えてから
 * 止まる」を状態で表さないと、<b>止めたのに実行が残っている状態を誰も読めない</b>
 * ——画面は「停止中」と出し、裏では実行が続くことになる。</p>
 */
public enum ScheduleStatus {

    /** 止まっている。次の実行を始めない。 */
    STOPPED("停止中"),

    /** 動いている。上限の範囲で次の実行を始める。 */
    RUNNING("実行中"),

    /** 止める途中。<b>新しい実行は始めないが、走っている実行は決着させる</b>。 */
    STOPPING("停止処理中");

    private final String label;

    ScheduleStatus(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }

    /** 新しい実行を始めてよいか。 */
    public boolean acceptsNewRuns() {
        return this == RUNNING;
    }

    /** 止まりきったか。 */
    public boolean isStopped() {
        return this == STOPPED;
    }
}
