package com.example.cargotracker.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * デモモード中の帯が<strong>どの業務画面にも出て、そこから止められる</strong>ことを
 * 確かめる。
 *
 * <p><strong>止める手段が見えない自動実行は、ただの暴走である。</strong> 帯は共通の
 * ナビゲーション（{@code layout/nav.html}）に置いており、40 を超える画面がそれを読む。
 * <strong>1 つでも帯の出ない画面があれば、そこを開いている利用者は止められない。</strong>
 *
 * <p><strong>ロールごとに開ける画面が違う</strong>ため、ロールを変えて確かめる。
 */
class DemoModeBannerTest extends PostgreSQLIntegrationTestBase {

    /**
     * ロール別の代表的な業務画面。
     *
     * <p><strong>「そのロールが最初に開く画面」を選ぶ。</strong> 帯はどこにでも出るが、
     * 出ていることに意味があるのは<strong>実際に開いたままにする画面</strong>である。
     */
    private record Screen(String path, String role) {
    }

    private static final List<Screen> SCREENS = List.of(
            new Screen("/", "SALES"),
            new Screen("/bookings", "SALES"),
            new Screen("/shippers", "SALES"),
            new Screen("/routing/queue", "ROUTER"),
            new Screen("/voyages", "ROUTER"),
            new Screen("/tracking/queue", "TRACKER"),
            new Screen("/tracking/exceptions", "TRACKER"),
            new Screen("/handling", "HANDLER"),
            new Screen("/handling/customs", "HANDLER"),
            new Screen("/billing/pending", "BILLING"),
            new Screen("/admin/accounts", "ADMIN"));

    @Autowired
    private DemoModeService demoMode;

    @AfterEach
    void stopDemoMode() {
        demoMode.stop();
    }

    @Test
    void 動いているあいだはどの業務画面にも帯が出る() throws Exception {
        demoMode.start();

        for (Screen screen : SCREENS) {
            mockMvc.perform(get(screen.path()).with(user("demo").roles(screen.role())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("demo-mode-banner")))
                    .andExpect(content().string(containsString("/public/demo/stop")))
                    .andExpect(content().string(containsString("/js/demo-mode.js")));
        }
    }

    /**
     * <strong>止めているあいだは帯を出さない。</strong> 出たままだと、
     * 動いているのか止まっているのか画面から判断できない。
     */
    @Test
    void 止めているあいだは帯が出ない() throws Exception {
        mockMvc.perform(get("/").with(user("demo").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("demo-mode-banner"))))
                .andExpect(content().string(not(containsString("/js/demo-mode.js"))));
    }

    /**
     * <strong>見ていた画面へ戻す。</strong> 帯から止めるたびに専用の画面へ飛ばされると、
     * 見ていた一覧を見失う。
     */
    @Test
    void 帯から止めると見ていた画面へ戻る() throws Exception {
        demoMode.start();

        mockMvc.perform(post("/public/demo/stop")
                        .header("Referer", "http://localhost/bookings?page=2")
                        .with(csrf()))
                .andExpect(redirectedUrl("/bookings?page=2"));
    }

    /**
     * <strong>外から渡された URL へ飛ばさない。</strong> このアプリの操作が
     * 外部サイトへの誘導に使えてはならない。
     */
    @Test
    void 別のサイトへは戻さない() throws Exception {
        demoMode.start();

        mockMvc.perform(post("/public/demo/stop")
                        .header("Referer", "https://example.com/phishing")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?demoStopped"));
    }
}
