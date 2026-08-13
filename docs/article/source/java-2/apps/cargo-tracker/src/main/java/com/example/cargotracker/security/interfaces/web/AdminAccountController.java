package com.example.cargotracker.security.interfaces.web;

import com.example.cargotracker.security.application.internal.commandservices.UnlockAccountCommandService;
import com.example.cargotracker.security.application.internal.queryservices.LockedAccountQueryService;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ロックされたアカウントの管理（US33）。
 *
 * <p>アクセスできるのは ROLE_ADMIN のみ（{@code SecurityConfig}）。
 */
@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private static final String VIEW = "admin/accounts";
    private static final String UNKNOWN_ACTOR = "unknown";

    private final LockedAccountQueryService queryService;
    private final UnlockAccountCommandService unlockService;

    public AdminAccountController(
            LockedAccountQueryService queryService,
            UnlockAccountCommandService unlockService) {
        this.queryService = queryService;
        this.unlockService = unlockService;
    }

    /** ロック中のアカウント一覧。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", queryService.findLocked());
        return VIEW;
    }

    /**
     * ロックを解除する。
     *
     * <p>理由が空なら解除しない。<strong>画面で必須にするだけでは、
     * 再送や URL の直叩きで通ってしまう。</strong>
     */
    @PostMapping("/{username}/unlock")
    public String unlock(
            @PathVariable("username") String username,
            @RequestParam(name = "reason", required = false) String reason,
            Principal principal,
            Model model,
            RedirectAttributes redirect) {

        if (reason == null || reason.isBlank()) {
            model.addAttribute("accounts", queryService.findLocked());
            model.addAttribute("unlockError", "解除の理由を入力してください");
            return VIEW;
        }

        unlockService.unlock(username, reason,
                        principal == null ? UNKNOWN_ACTOR : principal.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "対象の利用者が見つかりません"));

        redirect.addFlashAttribute("flashSuccess",
                username + " のロックを解除しました");
        return "redirect:/admin/accounts";
    }
}
