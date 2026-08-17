package com.example.cargotracker.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 自動実行 1 回分の進み具合。
 *
 * <p><strong>書く側と読む側が別のスレッドである。</strong> 手順を進めるのは実行用の
 * スレッドであり、進み具合を読むのは画面からの要求を処理するスレッドである。
 * 素の {@code ArrayList} を渡すと、<strong>読んでいる最中に足されて例外になる</strong>。
 *
 * <p><strong>状態は最後にまとめて書き換えない。</strong> 1 手ごとに記録する。
 * 終わってから書くと、途中で止まったときに<strong>どこまで進んだかが残らない</strong>。
 */
final class DemoAutopilotRun {

    /** 実行全体の状態。 */
    enum State {
        /** 実行中。 */
        RUNNING,
        /** 最後まで通った。 */
        COMPLETED,
        /** 途中で止まった。 */
        FAILED
    }

    /** 手順ひとつの状態。 */
    enum StepState {
        /** 実行中。 */
        RUNNING,
        /** 終わった。 */
        DONE,
        /** 失敗した。 */
        FAILED
    }

    /**
     * 手順ひとつの記録。
     *
     * @param number 何手目か（1 始まり）
     * @param title  手順の名前（画面に出す）
     * @param actor  その手順を行う担当（画面に出す）
     * @param detail 結果の要約（追跡番号など。まだ無ければ空）
     * @param state  状態
     * @param at     記録した時刻
     */
    record Step(int number, String title, String actor, String detail, StepState state,
            Instant at) {

        Step finished(String detail) {
            return new Step(number(), title(), actor(), detail, StepState.DONE, at());
        }

        Step failed(String reason) {
            return new Step(number(), title(), actor(), reason, StepState.FAILED, at());
        }
    }

    private final UUID id = UUID.randomUUID();
    private final DemoScenario scenario;
    private final Instant startedAt;
    private final List<Step> steps = new CopyOnWriteArrayList<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final AtomicReference<String> failureReason = new AtomicReference<>();
    private final AtomicReference<String> trackingNumber = new AtomicReference<>();
    private final AtomicReference<String> bookingId = new AtomicReference<>();

    DemoAutopilotRun(DemoScenario scenario, Instant startedAt) {
        this.scenario = scenario;
        this.startedAt = startedAt;
    }

    UUID id() {
        return id;
    }

    DemoScenario scenario() {
        return scenario;
    }

    Instant startedAt() {
        return startedAt;
    }

    State state() {
        return state.get();
    }

    String failureReason() {
        return failureReason.get();
    }

    String trackingNumber() {
        return trackingNumber.get();
    }

    String bookingId() {
        return bookingId.get();
    }

    /** 画面に出す順で手順を返す。 */
    List<Step> steps() {
        return List.copyOf(steps);
    }

    /** 総手順数。<strong>始まる前から分かっていないと進捗率を出せない。</strong> */
    int totalSteps() {
        return DemoAutopilotService.STEP_COUNT;
    }

    void trackingNumber(String value) {
        trackingNumber.set(value);
    }

    void bookingId(String value) {
        bookingId.set(value);
    }

    /** 手順を始めた。 */
    Step begin(String title, String actor, Instant at) {
        Step step = new Step(steps.size() + 1, title, actor, "", StepState.RUNNING, at);
        steps.add(step);
        return step;
    }

    /** 手順が終わった。 */
    void succeed(Step step, String detail) {
        replace(step, step.finished(detail));
    }

    /** 手順が失敗した。<strong>ここで実行全体も止まる。</strong> */
    void fail(Step step, String reason) {
        replace(step, step.failed(reason));
        failureReason.set(reason);
        state.set(State.FAILED);
    }

    /** 最後まで通った。 */
    void complete() {
        state.compareAndSet(State.RUNNING, State.COMPLETED);
    }

    private void replace(Step before, Step after) {
        int index = steps.indexOf(before);
        if (index >= 0) {
            steps.set(index, after);
        }
    }
}
