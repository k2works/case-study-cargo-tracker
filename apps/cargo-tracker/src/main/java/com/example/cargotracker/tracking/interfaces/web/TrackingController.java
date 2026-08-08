package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingInquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 追跡照会の画面（US18。<strong>要認証</strong>）。
 *
 * <p>荷主・荷受人・追跡管理者が使う（{@code ui_design.md} のナビゲーション構成）。
 *
 * <p><strong>一覧は出さない。</strong> 追跡番号を入力して 1 件を開くまでとする。
 * 利用者アカウントと荷主を結びつける手段がまだ無く、一覧を出すと
 * <strong>他社の貨物まで見える</strong>。荷主が自社の貨物を並べて見る画面は
 * US34（IT9）で紐付けを作ってから開く。
 *
 * <p><strong>PRG を使わない。</strong> 照会は副作用を持たず、
 * <strong>URL がそのまま共有可能である</strong>ことに意味がある。
 */
@Controller
@RequestMapping("/tracking")
public class TrackingController {

    private static final String VIEW_INPUT = "tracking/index";
    private static final String VIEW_DETAIL = "tracking/show";

    /**
     * 見つからないときのことば。
     *
     * <p><strong>存在しない番号と権限外の番号を区別しない。</strong> 区別すると、
     * 番号の総当たりで貨物の有無を確かめられる。
     */
    private static final String NOT_FOUND_MESSAGE =
            "該当する貨物が見つかりません。追跡番号を確認の上、再度お試しください。";

    private final TrackingInquiryService inquiryService;

    public TrackingController(TrackingInquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /** 追跡番号の入力画面。番号が渡されていればそのまま照会する。 */
    @GetMapping
    public String index(
            @RequestParam(name = "trackingNumber", required = false) String trackingNumber,
            Model model) {

        model.addAttribute("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return VIEW_INPUT;
        }
        return inquiryService.findByTrackingNumber(trackingNumber)
                .map(view -> {
                    model.addAttribute("tracking", view);
                    return VIEW_DETAIL;
                })
                .orElseGet(() -> {
                    model.addAttribute("notFound", NOT_FOUND_MESSAGE);
                    return VIEW_INPUT;
                });
    }

    /**
     * 追跡詳細。<strong>QR コード・共有 URL からの直アクセスの入口</strong>である。
     *
     * <p>見つからない場合も 404 にせず入力画面へ戻す。404 のページは
     * 「番号は正しいがシステムが壊れている」と読まれやすい。
     */
    @GetMapping("/{trackingNumber}")
    public String show(@PathVariable String trackingNumber, Model model) {
        model.addAttribute("trackingNumber", trackingNumber);
        return inquiryService.findByTrackingNumber(trackingNumber)
                .map(view -> {
                    model.addAttribute("tracking", view);
                    return VIEW_DETAIL;
                })
                .orElseGet(() -> {
                    model.addAttribute("notFound", NOT_FOUND_MESSAGE);
                    return VIEW_INPUT;
                });
    }
}
