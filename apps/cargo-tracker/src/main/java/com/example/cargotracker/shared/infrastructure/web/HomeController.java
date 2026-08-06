package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** ログイン・ダッシュボード・公開追跡の画面。 */
@Controller
public class HomeController {

    private final DemoLoginProperties demoLogin;

    public HomeController(DemoLoginProperties demoLogin) {
        this.demoLogin = demoLogin;
    }

    /**
     * ログイン画面。
     *
     * <p>開発環境では認証情報を事前入力する（{@code app.demo-login.enabled}）。
     * <strong>既定は無効であり、有効化した環境でのみ入る。</strong>
     */
    @GetMapping("/login")
    public String login(Model model) {
        if (demoLogin.enabled()) {
            model.addAttribute("demoLogin", demoLogin);
        }
        return "auth/login";
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 公開貨物追跡（認証不要）。
     *
     * <p>担当者名・荷主の住所などの個人情報は表示しない。荷主が取引先に URL を
     * 転送するのは日常的に起きるため（{@code ui_design.md}）。
     */
    @GetMapping("/public/tracking")
    public String publicTracking() {
        return "public/tracking";
    }
}
