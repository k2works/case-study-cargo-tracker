package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices
        .CancelBookingApprovalCommandService;
import com.example.cargotracker.booking.application.internal.queryservices
        .CancellationQueryService;
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
 * 輸送中の予約キャンセルの承認（US30。遷移表 #10）。
 *
 * <p><strong>承認・却下のあとは承認待ち一覧へ戻す。</strong> 追跡管理者は続けて
 * 複数件を捌く。<strong>予約詳細へ戻すと、そこから一覧へ戻れない</strong> —
 * `/bookings` は営業担当者と荷主のみである。
 *
 * <p>認可の規則は {@code SecurityConfig} が URL ごとに定める。
 * <strong>`/bookings/**` より前に置かないと 403 になる</strong>
 * （`/bookings/cancellations/{id}` は 2 セグメントであり `GET /bookings/*` に
 * 一致しない）。
 */
@Controller
@RequestMapping("/bookings/cancellations")
public class CancellationApprovalController {

    private static final String REDIRECT_QUEUE = "redirect:/bookings/cancellations";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String FLASH_ERROR = "flashError";
    private static final String UNKNOWN_ACTOR = "unknown";
    private static final String NOT_FOUND_MESSAGE = "キャンセルの申請が見つかりません";

    /**
     * 同時操作で先を越されたときの文言。
     *
     * <p><strong>操作ごとに書き分けない。</strong> 利用者から見て起きたことは同じである。
     */
    private static final String CONFLICT_MESSAGE =
            "他の担当者が先に決定しました。最新の内容を確認してください";

    private final CancellationQueryService queryService;
    private final CancelBookingApprovalCommandService approvalService;

    public CancellationApprovalController(
            CancellationQueryService queryService,
            CancelBookingApprovalCommandService approvalService) {
        this.queryService = queryService;
        this.approvalService = approvalService;
    }

    /**
     * 承認待ち一覧（US30 の受入基準 2）。
     *
     * <p><strong>古い順に並べる。</strong> 待たせている申請から捌く。
     * <strong>絞り込みは持たない</strong>（US36 の訂正申請の一覧と同じ形）。
     */
    @GetMapping
    public String queue(Model model) {
        model.addAttribute("requests", queryService.findPending());
        return "booking/cancellations";
    }

    /** 承認の画面（<strong>陸揚げ地を選ぶ</strong>）。 */
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") long id, Model model) {
        var view = queryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        model.addAttribute("request", view);
        return "booking/cancellation";
    }

    /** 承認する（US30 の受入基準 3・4）。 */
    @PostMapping("/{id}/approval")
    public String approve(
            @PathVariable("id") long id,
            @RequestParam(value = "discharge", required = false) String discharge,
            Principal principal,
            RedirectAttributes redirect) {
        return apply(
                approvalService.approve(id, discharge, actorOf(principal)),
                redirect, id, "キャンセルを承認しました。キャンセル料は請求に引き渡されます");
    }

    /** 却下する（US30 の受入基準 5）。<strong>輸送中のまま維持される。</strong> */
    @PostMapping("/{id}/rejection")
    public String reject(
            @PathVariable("id") long id,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal,
            RedirectAttributes redirect) {
        return apply(
                approvalService.reject(id, reason, actorOf(principal)),
                redirect, id, "キャンセルの申請を却下しました。輸送は続きます");
    }

    /**
     * 結果を画面へ返す。
     *
     * <p><strong>拒んだときは同じ画面に戻す</strong>（自己ループ）。一覧へ戻すと、
     * 何が悪かったのかを見ながら直せない。
     */
    private String apply(
            CancelBookingApprovalCommandService.Result result,
            RedirectAttributes redirect, long id, String successMessage) {
        switch (result.outcome()) {
            case SUCCEEDED -> {
                redirect.addFlashAttribute(FLASH_SUCCESS, successMessage);
                return REDIRECT_QUEUE;
            }
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case CONFLICTED -> redirect.addFlashAttribute(FLASH_ERROR, CONFLICT_MESSAGE);
            default -> redirect.addFlashAttribute(FLASH_ERROR, result.reason());
        }
        return REDIRECT_QUEUE + "/" + id;
    }

    private static String actorOf(Principal principal) {
        return principal == null ? UNKNOWN_ACTOR : principal.getName();
    }
}
