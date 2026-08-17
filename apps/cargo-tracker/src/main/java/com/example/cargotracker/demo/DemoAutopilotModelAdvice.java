package com.example.cargotracker.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 自動実行デモが使えるかを画面へ知らせる。
 *
 * <p><strong>ログイン画面のコントローラから触らない。</strong> {@code HomeController} は
 * {@code shared} にあり、<strong>そこから {@code demo} を参照すると依存の向きが逆になる</strong>
 * —— 本番の画面が動作確認用の仕組みを知ることになる。向きを保ったまま
 * 画面へ値を渡すために、{@code demo} の側から差し込む。
 *
 * <p><strong>無効なら {@code false} を差し込む。</strong> テンプレートの {@code th:if} は
 * {@code false} でも描かない。<strong>「変数が無い」で表すと、差し込み忘れと
 * 無効の区別が付かない。</strong>
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@ControllerAdvice
class DemoAutopilotModelAdvice {

    private final DemoAutopilotProperties properties;

    DemoAutopilotModelAdvice(DemoAutopilotProperties properties) {
        this.properties = properties;
    }

    /** 自動実行が使えるか。 */
    @ModelAttribute("demoAutopilot")
    boolean demoAutopilot() {
        return properties.enabled();
    }
}
