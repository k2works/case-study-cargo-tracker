package com.example.cargotracker.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

@Controller
public class HomeController {

    // cf. プロファイル名 "product" は本番環境を示す（ADR-001 参照）
    private static final String PRODUCTION_PROFILE = "product";

    private final Environment env;

    @Value("${spring.security.user.name:}")
    private String devUsername;

    public HomeController(Environment env) {
        this.env = env;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        // 本番プロファイル（"product"）以外では開発者向けのユーザー名自動入力を有効化する。
        // パスワードは application.yml を直接参照すること（セキュリティリスクのため Model に渡さない）。
        boolean isDevelopmentMode = Arrays.stream(env.getActiveProfiles())
                .noneMatch(PRODUCTION_PROFILE::equals);
        if (isDevelopmentMode && !devUsername.isEmpty()) {
            model.addAttribute("devUsername", devUsername);
        }
        return "login";
    }
}
