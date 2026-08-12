package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices
        .CorrectionCommandService;
import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionQueryService;
import com.example.cargotracker.handling.application.internal.commandservices
        .CorrectionCommandService.Result.Outcome;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionRequestType;
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
 * 引取記録の訂正・取り消し（US36）。
 *
 * <p><strong>申請は荷役作業員、承認は追跡管理者</strong>である。
 * 認可は {@code SecurityConfig} が持つ（画面に出さないことは認可ではない）。
 */
@Controller
@RequestMapping("/handling/corrections")
public class CorrectionController {

    private static final String FLASH_ERROR = "flashError";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String REDIRECT_LIST = "redirect:/handling/corrections";
    private static final String UNKNOWN_ACTOR = "unknown";

    /** 一覧に出す件数。**決定済みも含むため上限を置く。** */
    private static final int RECENT_LIMIT = 50;

    private final CorrectionCommandService commandService;
    private final CorrectionQueryService queryService;
    private final java.time.Clock clock;

    public CorrectionController(
            CorrectionCommandService commandService, CorrectionQueryService queryService,
            java.time.Clock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.clock = clock;
    }

    /**
     * 承認待ちの一覧（追跡管理者の作業入口）。
     *
     * <p><strong>件数を出すだけでは仕事は進まない。</strong> ここから 1 件ずつ
     * 承認・却下できる（IT9 のふりかえり T2）。
     */
    @GetMapping
    public String list(Principal principal, Model model) {
        // **決まった申請も残す。** 承認待ちだけを出すと、決まった瞬間に消え、
        // 申請した荷役作業員には承認か却下かも、却下の理由も届かない
        model.addAttribute("requests", queryService.findRecent(RECENT_LIMIT));
        // **誰が見ているか**（C9）。申請した本人には承認ボタンを出さない。
        // 兼務の拠点では、追跡管理者が自分で申請することが日常的に起きる
        model.addAttribute("viewer", actorOf(principal));
        return "handling/corrections";
    }

    /** 申請フォーム（荷役作業員）。 */
    @GetMapping("/new")
    public String form(
            @RequestParam(name = "handlingId") long handlingId, Model model) {
        model.addAttribute("handlingId", handlingId);
        model.addAttribute("types", CorrectionRequestType.values());
        return "handling/correction-form";
    }

    /** 申請する（荷役作業員）。<strong>理由は必須である。</strong> */
    @PostMapping
    public String request(
            @RequestParam("handlingId") long handlingId,
            @RequestParam("type") String type,
            @RequestParam(name = "reason", required = false) String reason,
            // 訂正で置き換える値（取り消しでは使わない）
            @RequestParam(name = "correctedCompletionTime", required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime correctedTime,
            @RequestParam(name = "correctedNote", required = false) String correctedNote,
            Principal principal,
            RedirectAttributes redirect) {

        CorrectionRequestType requestType;
        try {
            requestType = CorrectionRequestType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }

        var result = commandService.request(
                handlingId, requestType, reason, actorOf(principal),
                // **業務のタイムゾーンで読む。** JVM 既定だと実行環境が UTC のとき
                // 9 時間ずれた日時が記録される
                correctedTime == null ? null : correctedTime.atZone(clock.getZone()).toInstant(),
                correctedNote);
        switch (result.outcome()) {
            case NOT_FOUND -> throw notFound();
            case REJECTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return "redirect:/handling/corrections/new?handlingId=" + handlingId;
            }
            default -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "%sを申請しました。追跡管理者の承認をお待ちください"
                            .formatted(requestType.displayName()));
        }
        return "redirect:/handling";
    }

    /** 承認する（追跡管理者）。<strong>申請した本人は承認できない。</strong> */
    @PostMapping("/{requestId}/approval")
    public String approve(
            @PathVariable("requestId") long requestId,
            Principal principal,
            RedirectAttributes redirect) {
        CorrectionCommandService.Result result;
        try {
            result = commandService.approve(requestId, actorOf(principal));
        } catch (java.util.ConcurrentModificationException e) {
            // **同時に決定した。** 500 にしない — 利用者にできるのは開き直すことである
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_LIST;
        }
        // **種別で出し分ける。** 訂正の承認に「貨物の状態を戻しています」と出すと、
        // 戻してもいないし直してもいないことを二重に取り違えさせる
        return finish(result, redirect, result.outcome() == Outcome.ACCEPTED
                ? approvedMessage(requestId)
                : "承認しました");
    }

    /** 却下する（追跡管理者）。<strong>理由を残す。</strong> */
    @PostMapping("/{requestId}/rejection")
    public String reject(
            @PathVariable("requestId") long requestId,
            @RequestParam(name = "reason", required = false) String reason,
            Principal principal,
            RedirectAttributes redirect) {
        CorrectionCommandService.Result result;
        try {
            result = commandService.reject(requestId, actorOf(principal), reason);
        } catch (java.util.ConcurrentModificationException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_LIST;
        }
        return finish(result, redirect, "却下しました");
    }

    /** 承認したものが取り消しか訂正かで、起きたことが違う。 */
    private String approvedMessage(long requestId) {
        return queryService.findRecent(RECENT_LIMIT).stream()
                .filter(request -> request.id() == requestId)
                .findFirst()
                .map(request -> "取り消し".equals(request.typeLabel())
                        ? "取り消しを承認しました。貨物の状態を引取前に戻しています"
                        : "訂正を承認しました。記録の内容を直しました")
                .orElse("承認しました");
    }

    private String finish(
            CorrectionCommandService.Result result, RedirectAttributes redirect,
            String success) {
        switch (result.outcome()) {
            case NOT_FOUND -> throw notFound();
            case REJECTED -> redirect.addFlashAttribute(FLASH_ERROR, result.reason());
            default -> redirect.addFlashAttribute(FLASH_SUCCESS, success);
        }
        return REDIRECT_LIST;
    }

    private static String actorOf(Principal principal) {
        return principal == null ? UNKNOWN_ACTOR : principal.getName();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "対象の申請が見つかりません");
    }
}
