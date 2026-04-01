package com.example.cargotracker.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

@Controller
public class HomeController {

    private final Environment env;

    @Value("${spring.security.user.name:}")
    private String devUsername;

    @Value("${spring.security.user.password:}")
    private String devPassword;

    public HomeController(Environment env) {
        this.env = env;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        boolean isDevProfile = !Arrays.asList(env.getActiveProfiles()).contains("product");
        if (isDevProfile && !devUsername.isEmpty()) {
            model.addAttribute("devUsername", devUsername);
            model.addAttribute("devPassword", devPassword);
        }
        return "login";
    }
}
