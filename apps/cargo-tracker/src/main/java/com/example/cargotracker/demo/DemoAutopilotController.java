package com.example.cargotracker.demo;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 自動実行デモの画面（{@code /public/demo}）。
 *
 * <p><strong>認証の外に置く。</strong> ログイン画面のボタンから始めるため、
 * 認証済みでなければ到達できない場所に置くと<strong>ボタンを押した先で
 * ログインを求められる</strong>。{@code /public/**} は認証不要として設定済みであり
 * （{@code SecurityConfig}）、公開追跡画面と同じ扱いになる。
 *
 * <p><strong>だから有効化を二重にしている。</strong> 認証の外にある以上、
 * 「開発環境でだけ動く」ことをこの画面自身が確かめる必要がある。
 * Bean が作られる条件（{@code cargo-tracker.demo.install}）と、
 * 要求ごとの確認（{@code cargo-tracker.demo.autopilot.enabled}）の両方を通る。
 * <strong>設定を消し忘れても、片方が false なら 404 になる。</strong>
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Controller
@RequestMapping("/public/demo")
class DemoAutopilotController {

    private final DemoAutopilotService autopilot;
    private final DemoAutopilotCleanup cleanup;
    private final DemoAutopilotProperties properties;

    DemoAutopilotController(
            DemoAutopilotService autopilot, DemoAutopilotCleanup cleanup,
            DemoAutopilotProperties properties) {
        this.autopilot = autopilot;
        this.cleanup = cleanup;
        this.properties = properties;
    }

    /** 自動実行を始める。<strong>すぐ進捗画面へ送る</strong>（実行は裏で進む）。 */
    @PostMapping("/start")
    String start() {
        requireEnabled();
        DemoAutopilotRun run = autopilot.start();
        return "redirect:/public/demo/" + run.id();
    }

    /** 進捗画面。 */
    @GetMapping("/{runId}")
    String show(@PathVariable UUID runId, Model model) {
        requireEnabled();
        DemoAutopilotRun run = autopilot.find(runId).orElseThrow(this::notFound);
        model.addAttribute("runId", run.id());
        model.addAttribute("scenario", run.scenario().summary());
        model.addAttribute("pendingCleanup", cleanup.pending());
        return "public/demo";
    }

    /**
     * 進み具合を返す（画面が繰り返し読みに来る）。
     *
     * <p><strong>画面ごと返さない。</strong> 手順が 1 つ進むたびに画面全体を作り直すと、
     * 読んでいる途中で先頭に戻る。
     */
    @GetMapping("/{runId}/progress")
    @ResponseBody
    Progress progress(@PathVariable UUID runId) {
        requireEnabled();
        DemoAutopilotRun run = autopilot.find(runId).orElseThrow(this::notFound);
        return Progress.of(run);
    }

    /** 自動実行が作ったデータをまとめて片付ける。 */
    @PostMapping("/reset")
    String reset() {
        requireEnabled();
        cleanup.reset();
        autopilot.forgetAll();
        return "redirect:/login?demoReset";
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw notFound();
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /**
     * 画面に返す進み具合。
     *
     * @param state          実行全体の状態
     * @param finished       終わったか（画面が読み込みを止める判断に使う）
     * @param totalSteps     手順の総数
     * @param trackingNumber 追跡番号（発行前は null）
     * @param bookingId      予約 ID（登録前は null）
     * @param failureReason  止まった理由（止まっていなければ null）
     * @param steps          手順ごとの記録
     */
    record Progress(
            String state,
            boolean finished,
            int totalSteps,
            String trackingNumber,
            String bookingId,
            String failureReason,
            List<Step> steps) {

        static Progress of(DemoAutopilotRun run) {
            return new Progress(
                    run.state().name(),
                    run.state() != DemoAutopilotRun.State.RUNNING,
                    run.totalSteps(),
                    run.trackingNumber(),
                    run.bookingId(),
                    run.failureReason(),
                    run.steps().stream().map(Step::of).toList());
        }

        /**
         * 手順ひとつ。
         *
         * @param number 何手目か
         * @param title  手順の名前
         * @param actor  担当
         * @param detail 結果の要約
         * @param state  状態
         */
        record Step(int number, String title, String actor, String detail, String state) {

            static Step of(DemoAutopilotRun.Step step) {
                return new Step(step.number(), step.title(), step.actor(),
                        step.detail(), step.state().name());
            }
        }
    }
}
