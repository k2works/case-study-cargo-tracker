package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** ログイン・ダッシュボード・公開追跡の画面。 */
@Controller
public class HomeController {

    @GetMapping("/login")
    public String login() {
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
