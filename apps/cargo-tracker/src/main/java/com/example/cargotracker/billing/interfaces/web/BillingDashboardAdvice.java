package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ダッシュボードの「未請求の引取済貨物」カード（US21。ADR-014）。
 *
 * <p><strong>既存の 3 カードはいずれも請求書がある貨物を数えている</strong>
 * （未払い請求・支払期限超過・今月の請求総額）。<strong>まだ請求書が無い貨物に
 * 気づく手段が無かった。</strong>
 *
 * <p><strong>件数を出すだけでは仕事は進まない</strong>（IT9 のふりかえり T2）。
 * カードから請求対象一覧へ行ける。
 *
 * <p><strong>対象は {@code DashboardController} である</strong>（IT13 で取り違えた）。
 * ダッシュボードを描くのはこちらであり、{@code HomeController} を指定すると
 * <strong>件数が空欄のまま「件」だけが並ぶ</strong>。見出しの存在だけを検査していると
 * 気づかない — <strong>「気づく手段」が数を出せていなければ、気づけない</strong>。
 */
@ControllerAdvice(assignableTypes = com.example.cargotracker.shared.infrastructure.web
        .DashboardController.class)
public class BillingDashboardAdvice {

    private final BillingQueryService queryService;

    /** 期限超過の判定（US23）。<strong>画面を開いたときに走らせる。</strong> */
    private final com.example.cargotracker.billing.application.internal.commandservices
            .SettleInvoiceCommandService settleService;

    public BillingDashboardAdvice(
            BillingQueryService queryService,
            com.example.cargotracker.billing.application.internal.commandservices
                    .SettleInvoiceCommandService settleService) {
        this.queryService = queryService;
        this.settleService = settleService;
    }

    /**
     * 未請求の引取済貨物の件数。
     *
     * <p><strong>経理担当者にだけ数える。</strong> 他のロールには使われない数を
     * 毎回引かない。
     */
    @ModelAttribute("pendingBillingCount")
    public int pendingBillingCount(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_BILLING".equals(a.getAuthority()))) {
            return 0;
        }
        return queryService.countPendingCargo();
    }

    /**
     * 支払期限を過ぎた請求書の件数（US23 の受入基準 5）。
     *
     * <p>US23 は「支払い期限超過時、経理担当者に未払い通知が送信される」と述べている。
     * <strong>送信の仕組みを先に作っても、受け取る人が見る場所が無ければ届かない。</strong>
     * まず<strong>画面で気づける形</strong>にする。
     *
     * <p><strong>数えるだけで終わらせない</strong>（IT9 のふりかえり T2）。
     * カードから督促対象の一覧へ行ける。
     */
    @ModelAttribute("overdueInvoiceCount")
    public int overdueInvoiceCount(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_BILLING".equals(a.getAuthority()))) {
            return 0;
        }
        // **開いたときに判定する。** 夜間バッチにすると、動いているかを誰も確かめない
        settleService.refreshOverdue();
        return queryService.countOverdueInvoices();
    }
}
