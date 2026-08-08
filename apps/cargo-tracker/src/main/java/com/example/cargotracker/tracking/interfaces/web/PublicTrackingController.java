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
 * 公開追跡の画面（US18。<strong>認証不要</strong>）。
 *
 * <p><strong>本システムで認証を持たない相手に見せる唯一の画面である。</strong>
 * 荷主が取引先へ URL を転送するのは日常的に起きるため、
 * <strong>見せてよい情報の範囲がそのまま設計上の制約になる</strong>。
 *
 * <p>守っていること。
 *
 * <ul>
 *   <li><strong>個人情報を返さない。</strong> 荷主名・住所・連絡先・担当者名は
 *       {@code TrackingInquiryView} に含まれない（型として持たない）</li>
 *   <li><strong>存在しない番号と形式の違う番号を同じことばで返す。</strong>
 *       区別すると、番号の総当たりで貨物の有無を確かめられる</li>
 *   <li><strong>共有できる URL を保つ。</strong> PRG を使わない。
 *       リダイレクトで番号を URL から消すと、荷主が取引先へ転送できない</li>
 * </ul>
 *
 * <p>過剰なアクセスへの防御はレートリミットが担う
 * （{@code non_functional.md}。{@code PublicRateLimitFilter}）。
 */
@Controller
@RequestMapping("/public/tracking")
public class PublicTrackingController {

    private static final String VIEW = "public/tracking";

    /**
     * 見つからないときのことば。
     *
     * <p><strong>認証つき画面と同じ文言にする。</strong> 違えると、
     * 応答の差から「認証すれば見えるものがある」ことが読み取れる。
     */
    private static final String NOT_FOUND_MESSAGE =
            "該当する貨物が見つかりません。追跡番号を確認の上、再度お試しください。";

    private final TrackingInquiryService inquiryService;

    public PublicTrackingController(TrackingInquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /** 入力フォームと照会結果。番号が渡されていればそのまま照会する。 */
    @GetMapping
    public String index(
            @RequestParam(name = "trackingNumber", required = false) String trackingNumber,
            Model model) {
        return render(trackingNumber, model);
    }

    /**
     * 追跡番号を直接指定して開く。
     *
     * <p><strong>QR コード・共有 URL からの入口である。</strong>
     * パス変数の名前は {@code trackingNumber} とする。{@code ui_design.md} の
     * 画面一覧は {@code trackingId} と書いていたが、<strong>{@code trackingId}
     * という型はどこにも無い</strong>（IT7 設計反映 #4）。
     */
    @GetMapping("/{trackingNumber}")
    public String show(@PathVariable String trackingNumber, Model model) {
        return render(trackingNumber, model);
    }

    private String render(String trackingNumber, Model model) {
        model.addAttribute("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            inquiryService.findByTrackingNumber(trackingNumber).ifPresentOrElse(
                    view -> model.addAttribute("tracking", view),
                    () -> model.addAttribute("notFound", NOT_FOUND_MESSAGE));
        }
        return VIEW;
    }
}
