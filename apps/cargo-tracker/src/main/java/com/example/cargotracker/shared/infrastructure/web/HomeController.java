package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

    /**
     * 権限不足の画面（403）。
     *
     * <p>{@code SecurityConfig} の accessDeniedHandler から forward される。
     * <strong>行き止まりを作らないため、ダッシュボードへの導線を必ず置く。</strong>
     *
     * <p><strong>メソッドを限定しない。</strong> GET だけに割り当てると、
     * 権限の無い POST が拒否されたときに forward 先で 405（Method Not Allowed）に
     * なり、<strong>利用者には「壊れている」としか見えない</strong>（Heroku 上で実測）。
     * MockMvc は forward のディスパッチが実サーブレットと異なるため、
     * この食い違いはテストでは現れなかった。
     */
    @RequestMapping(value = "/access-denied",
            method = {org.springframework.web.bind.annotation.RequestMethod.GET,
                      org.springframework.web.bind.annotation.RequestMethod.POST})
    public String accessDenied() {
        return "error/403";
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }
}
