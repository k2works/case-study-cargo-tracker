package com.example.cargotracker.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * デモモードの操作（{@code /public/demo}）。
 *
 * <p><strong>認証の外に置く。</strong> 開始はログイン画面のボタンから行うため、
 * 認証済みでなければ到達できない場所に置くと<strong>ボタンを押した先で
 * ログインを求められる</strong>。{@code /public/**} は認証不要として設定済みであり
 * （{@code SecurityConfig}）、公開追跡画面と同じ扱いになる。
 *
 * <p><strong>だから有効化を二重にしている。</strong> 認証の外にある以上、
 * 「開発環境でだけ動く」ことをこの入口自身が確かめる必要がある。
 * Bean が作られる条件（{@code cargo-tracker.demo.install}）と、要求ごとの確認
 * （{@code cargo-tracker.demo.mode.enabled}）の両方を通る。
 * <strong>設定を消し忘れても、片方が false なら 404 になる。</strong>
 *
 * <p><strong>操作したら元の画面へ戻す。</strong> 停止も片付けも業務画面の帯から押す。
 * 押すたびに専用の画面へ飛ばされると、<strong>見ていた一覧を見失う</strong>。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Controller
@RequestMapping("/public/demo")
class DemoModeController {

    private final DemoModeService demoMode;
    private final DemoModeCleanup cleanup;
    private final DemoModeProperties properties;

    DemoModeController(
            DemoModeService demoMode, DemoModeCleanup cleanup, DemoModeProperties properties) {
        this.demoMode = demoMode;
        this.cleanup = cleanup;
        this.properties = properties;
    }

    /** デモモードを入れる。 */
    @PostMapping("/start")
    String start(HttpServletRequest request) {
        requireEnabled();
        demoMode.start();
        return back(request, "/login?demoStarted");
    }

    /** デモモードを止める。<strong>作ったデータは残る。</strong> */
    @PostMapping("/stop")
    String stop(HttpServletRequest request) {
        requireEnabled();
        demoMode.stop();
        return back(request, "/login?demoStopped");
    }

    /**
     * デモモードが作ったデータをまとめて片付ける。
     *
     * <p><strong>先に止める。</strong> 動いたまま消すと、消している間に次の 1 手が
     * 走り、消し残しができる。
     */
    @PostMapping("/reset")
    String reset(HttpServletRequest request) {
        requireEnabled();
        demoMode.stop();
        cleanup.reset();
        demoMode.forgetAll();
        return back(request, "/login?demoReset");
    }

    /**
     * 状況を返す（帯が繰り返し読みに来る）。
     *
     * <p><strong>この問い合わせでは画面を再読み込みしない。</strong> 帯だけを
     * 書き換え、業務の中身は別の間隔で再読み込みする。
     */
    @GetMapping("/status")
    @ResponseBody
    DemoModeStatus status() {
        requireEnabled();
        return demoMode.status();
    }

    /**
     * 押す前に見ていた画面へ戻す。
     *
     * <p><strong>Referer をそのまま信じない。</strong> 外から渡された URL へ
     * 飛ばすと、<strong>このアプリの操作が外部サイトへの誘導に使える</strong>。
     * 自分のところの絶対パスだけを受け入れる。
     */
    private String back(HttpServletRequest request, String fallback) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "redirect:" + fallback;
        }
        String path = pathOf(referer, request);
        // **「/」で始まり「//」で始まらない**（`//example.com` は別ホストへ飛ぶ）
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            return "redirect:" + fallback;
        }
        return "redirect:" + path;
    }

    /** 同じホストの要求なら、そのパスとクエリを返す。<strong>違えば {@code null}。</strong> */
    private String pathOf(String referer, HttpServletRequest request) {
        try {
            java.net.URI uri = java.net.URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().equals(request.getServerName())) {
                return null;
            }
            String path = uri.getRawPath();
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
