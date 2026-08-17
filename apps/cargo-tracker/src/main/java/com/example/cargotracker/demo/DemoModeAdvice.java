package com.example.cargotracker.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * デモモードの状態を、すべての画面へ知らせる。
 *
 * <p><strong>業務のコントローラから触らない。</strong> ナビゲーションは {@code shared} に
 * あり、<strong>そこから {@code demo} を参照すると依存の向きが逆になる</strong>
 * —— 本番の画面が動作確認用の仕組みを知ることになる。向きを保ったまま
 * 画面へ値を渡すために、{@code demo} の側から差し込む。
 *
 * <p><strong>すべての画面に差し込む必要がある。</strong> デモモード中の帯は
 * 共通ナビゲーション（{@code layout/nav.html}）に置いており、それは 40 を超える
 * 画面が読み込む。特定のコントローラに限定すると、<strong>帯の出る画面と
 * 出ない画面ができ、出ない画面からは止められなくなる</strong>。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@ControllerAdvice
class DemoModeAdvice {

    private final DemoModeService demoMode;
    private final DemoModeProperties properties;

    DemoModeAdvice(DemoModeService demoMode, DemoModeProperties properties) {
        this.demoMode = demoMode;
        this.properties = properties;
    }

    /** デモモードが使えるか（ログイン画面のボタンの出し分け）。 */
    @ModelAttribute("demoModeEnabled")
    boolean demoModeEnabled() {
        return properties.enabled();
    }

    /**
     * デモモードが動いているか（帯と自動再読み込みの出し分け）。
     *
     * <p><strong>使えるだけでは帯を出さない。</strong> 止めているあいだも帯が出ていると、
     * 動いているのか止まっているのか画面から判断できない。
     */
    @ModelAttribute("demoModeRunning")
    boolean demoModeRunning() {
        return properties.enabled() && demoMode.running();
    }

    /** 業務画面を再読み込みする間隔（ミリ秒）。 */
    @ModelAttribute("demoModeRefreshMs")
    long demoModeRefreshMs() {
        return properties.refreshInterval().toMillis();
    }
}
