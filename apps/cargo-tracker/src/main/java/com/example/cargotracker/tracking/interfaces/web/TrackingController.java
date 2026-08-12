package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingInquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.cargotracker.tracking.application.internal.commandservices
        .UpdateTrackingStatusCommandService;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;

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

    /** 画面に渡す属性名。 */
    private static final String ATTR_TRACKING = "tracking";

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
    private final UpdateTrackingStatusCommandService updateService;
    private final java.time.Clock clock;

    public TrackingController(
            TrackingInquiryService inquiryService,
            UpdateTrackingStatusCommandService updateService,
            java.time.Clock clock) {
        this.inquiryService = inquiryService;
        this.updateService = updateService;
        this.clock = clock;
    }

    /**
     * 状態の手動更新（US17。<strong>ROLE_TRACKER のみ</strong>）。
     *
     * <p><strong>POST である。</strong> 同じパスの GET は存在しない。自動更新の取得先は
     * {@code /status-fragment} に分けてある（更新と参照で認可の対象が違う）。
     */
    @PostMapping("/{trackingNumber}/status")
    public String updateStatus(
            @PathVariable("trackingNumber") String trackingNumber,
            @RequestParam("eventType") String eventType,
            @RequestParam("location") String location,
            @RequestParam("occurredAt")
            @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime occurredAt,
            java.security.Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {

        TrackingEventType type;
        try {
            type = TrackingEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "不明な種別です");
        }
        // **手動更新で選べない種別は受け付けない。** 画面から消すだけでは、
        // リクエストを直接組み立てれば送れてしまう
        if (!type.manuallyUpdatable()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "手動更新で登録できない種別です");
        }

        var result = updateService.update(
                trackingNumber, type, location,
                occurredAt.atZone(businessZone()).toInstant(),
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case NOT_FOUND -> throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED, CONFLICTED -> redirect.addFlashAttribute("flashError", result.reason());
            default -> redirect.addFlashAttribute("flashSuccess", "貨物の状態を更新しました");
        }
        return "redirect:/tracking/" + trackingNumber;
    }

    /**
     * 自動更新で差し替える部分（{@code ui_design.md} htmx 節）。
     *
     * <p><strong>{@code /status} と分ける。</strong> 同じパスを更新（POST・ROLE_TRACKER）と
     * 参照で共有すると、認可の対象が違うのに片方の規則しか効かない（IT7 の突合）。
     */
    @GetMapping("/{trackingNumber}/status-fragment")
    public String statusFragment(
            @PathVariable("trackingNumber") String trackingNumber, Model model) {
        return inquiryService.findByTrackingNumber(trackingNumber)
                .map(view -> {
                    model.addAttribute(ATTR_TRACKING, view);
                    return "tracking/_body :: content";
                })
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
    }

    /**
     * 手動更新で選べる種別（US17）。
     *
     * <p><strong>列挙型に尋ねる。</strong> 画面にもここにも一覧を書き写さない。
     */
    @org.springframework.web.bind.annotation.ModelAttribute("manualEventTypes")
    public java.util.List<TrackingEventType> manualEventTypes() {
        return java.util.Arrays.stream(TrackingEventType.values())
                .filter(TrackingEventType::manuallyUpdatable)
                .toList();
    }

    /** 業務のタイムゾーン。**JVM 既定を使わない**（CI が UTC でずれる）。 */
    private java.time.ZoneId businessZone() {
        return clock.getZone();
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
                    model.addAttribute(ATTR_TRACKING, view);
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
                    model.addAttribute(ATTR_TRACKING, view);
                    return VIEW_DETAIL;
                })
                .orElseGet(() -> {
                    model.addAttribute("notFound", NOT_FOUND_MESSAGE);
                    return VIEW_INPUT;
                });
    }
}
