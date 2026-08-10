package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices
        .CalculateChargeCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import com.example.cargotracker.billing.domain.model.Adjustment;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.Money;
import java.math.BigDecimal;
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
 * 請求管理の画面（US21 / US22）。
 *
 * <p>アクセスできるのは ROLE_BILLING のみ（{@code SecurityConfig} の {@code /billing/**}）。
 * <strong>請求書は金額であり、見える範囲を誤ると他社の取引条件が漏れる。</strong>
 *
 * <p><strong>算出と確定を分ける。</strong> 受入基準「算出結果を確認して確定操作が
 * できる」は、経理担当者が目で見て確かめる場を求めている。
 */
@Controller
@RequestMapping("/billing")
public class BillingController {

    private static final String REDIRECT_INVOICE = "redirect:/billing/invoices/";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String FLASH_ERROR = "flashError";
    private static final String NOT_FOUND_MESSAGE = "精算書が見つかりません";
    private static final String UNKNOWN_ACTOR = "unknown";

    private final BillingQueryService queryService;
    private final CalculateChargeCommandService chargeService;

    public BillingController(
            BillingQueryService queryService, CalculateChargeCommandService chargeService) {
        this.queryService = queryService;
        this.chargeService = chargeService;
    }

    /**
     * 請求対象一覧（US21 の受入基準 1 の入口）。
     *
     * <p><strong>まだ請求書が無い貨物にたどり着く道である。</strong> 請求書一覧だけでは、
     * これから請求するものが見えない。
     */
    @GetMapping("/pending")
    public String pending(Model model) {
        model.addAttribute("cargos", queryService.findPendingCargo());
        return "billing/pending";
    }

    /** 請求書一覧。 */
    @GetMapping("/invoices")
    public String invoices(
            @RequestParam(value = "status", required = false) String status, Model model) {
        model.addAttribute("invoices", queryService.findInvoices(blankToNull(status)));
        model.addAttribute("status", status);
        return "billing/invoices";
    }

    /** 請求書詳細（<strong>割引の根拠を出す</strong>。US22 の受入基準 4）。 */
    @GetMapping("/invoices/{invoiceNumber}")
    public String invoice(@PathVariable("invoiceNumber") String invoiceNumber, Model model) {
        model.addAttribute("invoice", queryService.findInvoice(invoiceNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE)));
        return "billing/invoice";
    }

    /**
     * 料金を算出する（US21 / US22）。
     *
     * <p><strong>確定はしない。</strong> 下書きを作り、詳細画面へ送る。
     */
    @PostMapping("/invoices")
    public String calculate(
            @RequestParam("bookingId") String bookingId,
            Principal principal,
            RedirectAttributes redirect) {
        var result = chargeService.calculate(bookingId, actorOf(principal));
        return switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "対象の貨物が見つかりません");
            case REJECTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                yield "redirect:/billing/pending";
            }
            default -> {
                redirect.addFlashAttribute(FLASH_SUCCESS,
                        "料金を算出しました。内容を確認して確定してください");
                yield REDIRECT_INVOICE + result.invoiceId().value();
            }
        };
    }

    /** 料金調整を入力する（US21 の受入基準 6）。 */
    @PostMapping("/invoices/{invoiceNumber}/adjustment")
    public String adjust(
            @PathVariable("invoiceNumber") String invoiceNumber,
            @RequestParam(value = "reduction", defaultValue = "0") BigDecimal reduction,
            @RequestParam(value = "compensation", defaultValue = "0") BigDecimal compensation,
            @RequestParam("reason") String reason,
            Principal principal,
            RedirectAttributes redirect) {

        Adjustment adjustment;
        try {
            adjustment = new Adjustment(
                    Money.yen(reduction), Money.yen(compensation), reason);
        } catch (IllegalArgumentException e) {
            // **入力の誤りを 500 にしない。** 理由をそのまま画面へ返す
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return REDIRECT_INVOICE + invoiceNumber;
        }

        var result = chargeService.adjust(
                InvoiceId.of(invoiceNumber), adjustment, actorOf(principal));
        applyOutcome(result, redirect, "料金調整を反映しました");
        return REDIRECT_INVOICE + invoiceNumber;
    }

    /** 料金を確定する（US21）。<strong>確定後は金額が動かない。</strong> */
    @PostMapping("/invoices/{invoiceNumber}/confirmation")
    public String confirm(
            @PathVariable("invoiceNumber") String invoiceNumber,
            Principal principal,
            RedirectAttributes redirect) {
        var result = chargeService.confirm(InvoiceId.of(invoiceNumber), actorOf(principal));
        applyOutcome(result, redirect, "料金を確定しました");
        return REDIRECT_INVOICE + invoiceNumber;
    }

    private void applyOutcome(
            CalculateChargeCommandService.Result result,
            RedirectAttributes redirect,
            String successMessage) {
        switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED -> redirect.addFlashAttribute(FLASH_ERROR, result.reason());
            // **黙って上書きしない。** 他の担当者が先に確定した
            case CONFLICTED -> redirect.addFlashAttribute(FLASH_ERROR,
                    "他の担当者が先に更新しました。内容を確認し直してください");
            default -> redirect.addFlashAttribute(FLASH_SUCCESS, successMessage);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String actorOf(Principal principal) {
        return principal == null ? UNKNOWN_ACTOR : principal.getName();
    }
}
