package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

@Controller
public class HomeController {

    private final Environment env;
    private final DashboardQueryService dashboardQueryService;

    @Value("${spring.security.user.name:}")
    private String devUsername;

    @Value("${spring.security.user.password:}")
    private String devPassword;

    @Value("${app.seed.enabled:false}")
    private boolean seedDataEnabled;

    public HomeController(Environment env, DashboardQueryService dashboardQueryService) {
        this.env = env;
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("dashboard", dashboardQueryService.getSummary());
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        boolean showDemoNotice = seedDataEnabled
                && !Arrays.asList(env.getActiveProfiles()).contains("product")
                && !devUsername.isBlank();
        if (showDemoNotice) {
            model.addAttribute("devUsername", devUsername);
            model.addAttribute("devPassword", devPassword);
            model.addAttribute("showDemoNotice", true);
        }
        return "login";
    }
}
