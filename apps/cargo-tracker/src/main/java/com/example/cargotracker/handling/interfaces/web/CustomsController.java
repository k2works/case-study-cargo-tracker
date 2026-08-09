package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices
        .CustomsDeclarationCommandService;
import com.example.cargotracker.handling.application.internal.queryservices.CustomsQueryService;
import com.example.cargotracker.handling.domain.model.CustomsStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 通関申告の画面（US29）。
 *
 * <p><strong>PRG を使う。</strong> 登録も状態更新も副作用を持つため、
 * 再読込で二重に登録される形を作らない。
 */
@Controller
@RequestMapping("/handling/customs")
public class CustomsController {

    private static final String FLASH_ERROR = "flashError";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String REDIRECT_LIST = "redirect:/handling/customs";
    private static final String NOT_FOUND_MESSAGE = "該当する通関申告が見つかりません。";

    private final CustomsDeclarationCommandService commandService;
    private final CustomsQueryService queryService;

    /** **業務のタイムゾーンで日時を解釈する。** UTC で受けると時差の分だけずれる。 */
    private final Clock clock;

    public CustomsController(
            CustomsDeclarationCommandService commandService,
            CustomsQueryService queryService,
            Clock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.clock = clock;
    }

    /**
     * 通関申告一覧（受入基準「貨物 ID・追跡番号・通関状態で検索できる」）。
     *
     * <p><strong>既定では絞らない。</strong> 通関は件数が多くなく、
     * 「いま何が止まっているか」を一目で見せたい。留置は先頭に来る。
     */
    @GetMapping
    public String list(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            // **ダッシュボードのカードが数えた対象へそのまま行く**（C33）。
            // 「3 日を超えた申告」の件数を出しながら留置の全件へ飛ばすと、
            // どれが放置されているのかを開いた先で数え直すことになる
            @RequestParam(name = "days", required = false) Integer days,
            Model model) {
        model.addAttribute("declarations", queryService.search(keyword, status, days));
        model.addAttribute("heldDays", days);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statuses", CustomsStatus.values());
        return "handling/customs-list";
    }

    /** 通関申告の登録フォーム。追跡番号を埋めて開ける（番号を書き写させない）。 */
    @GetMapping("/new")
    public String newForm(
            @RequestParam(name = "trackingNumber", required = false) String trackingNumber,
            Model model) {
        model.addAttribute("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        model.addAttribute("defaultDeclaredAt",
                LocalDateTime.now(clock).withSecond(0).withNano(0));
        return "handling/customs-form";
    }

    /** 通関申告の詳細（状態更新フォームと変更履歴）。 */
    @GetMapping("/{declarationId}")
    public String detail(@PathVariable("declarationId") long declarationId, Model model) {
        var found = queryService.findById(declarationId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        model.addAttribute("declaration", found.get());
        model.addAttribute("history", queryService.findHistory(declarationId));
        model.addAttribute("statuses", CustomsStatus.values());
        return "handling/customs-detail";
    }

    /** 通関申告を登録する（US29）。 */
    @PostMapping
    public String declare(
            @RequestParam("trackingNumber") String trackingNumber,
            @RequestParam("declarationNumber") String declarationNumber,
            @RequestParam("declaredAt")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime declaredAt,
            RedirectAttributes redirect) {

        var result = commandService.declare(
                trackingNumber, declarationNumber,
                declaredAt.atZone(businessZone()).toInstant());

        switch (result.outcome()) {
            case NOT_FOUND -> {
                redirect.addFlashAttribute(FLASH_ERROR,
                        "追跡番号 %s の貨物が見つかりません。".formatted(trackingNumber));
                return "redirect:/handling/customs/new";
            }
            case REJECTED, CONFLICTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return "redirect:/handling/customs/new";
            }
            default -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "通関申告を登録しました。状態は「審査中」です");
        }
        return REDIRECT_LIST;
    }

    /** 通関状態を更新する（US29「更新時は理由の入力が必須」）。 */
    @PostMapping("/{declarationId}/status")
    public String updateStatus(
            @PathVariable("declarationId") long declarationId,
            @RequestParam("status") String status,
            @RequestParam(name = "reason", required = false) String reason,
            java.security.Principal principal,
            RedirectAttributes redirect) {

        CustomsStatus next;
        try {
            next = CustomsStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不明な通関状態です");
        }

        var result = commandService.updateStatus(
                declarationId, next, reason,
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED, CONFLICTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return "redirect:/handling/customs/" + declarationId;
            }
            default -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "通関状態を「%s」に更新しました".formatted(next.displayName()));
        }
        return "redirect:/handling/customs/" + declarationId;
    }

    /** 業務のタイムゾーン。**入力された日時はここで解釈する。** */
    private ZoneId businessZone() {
        return clock.getZone();
    }
}
