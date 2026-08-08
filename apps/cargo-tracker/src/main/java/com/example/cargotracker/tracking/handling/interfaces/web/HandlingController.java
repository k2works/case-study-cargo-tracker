package com.example.cargotracker.tracking.handling.interfaces.web;

import com.example.cargotracker.tracking.handling.application.internal.commandservices.RegisterHandlingCommandService;
import com.example.cargotracker.tracking.handling.domain.model.HandlingType;
import com.example.cargotracker.tracking.handling.application.internal.queryservices.HandlingQueryService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ConcurrentModificationException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 荷役作業の画面（US15）。<strong>荷役作業員が使う唯一の画面</strong>である。
 *
 * <p>登録は PRG パターンで一覧へ戻り、<strong>登録した作業を先頭に表示する</strong>
 * （{@code ui_design.md}）。自分が今スキャンした荷物を探し直させない。
 *
 * <p>URL が {@code /handling/*} で始まるのは、利用者から見た業務の区切りが荷役だから
 * である。実装上は Tracking Context の中のモジュールであり、<strong>URL の語と内部の
 * コンテキスト構成は一致しなくてよい</strong>（ADR-002）。
 */
@Controller
@RequestMapping("/handling")
public class HandlingController {

    private static final String VIEW_LIST = "handling/list";
    private static final String VIEW_FORM = "handling/form";
    private static final String FLASH_WARNING = "flashWarning";
    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String UNKNOWN_ACTOR = "unknown";

    /** 一覧に出す件数。**現場が見るのは直近の作業である**（ui_design.md）。 */
    private static final int RECENT_LIMIT = 50;

    private final RegisterHandlingCommandService registerService;
    private final HandlingQueryService queryService;
    private final Clock clock;

    public HandlingController(
            RegisterHandlingCommandService registerService,
            HandlingQueryService queryService,
            Clock clock) {
        this.registerService = registerService;
        this.queryService = queryService;
        this.clock = clock;
    }

    /** 荷役種別のプルダウン。**日本語ラベルの正典は列挙型が持つ。** */
    @ModelAttribute("handlingTypes")
    public Map<String, String> handlingTypes() {
        Map<String, String> labels = new LinkedHashMap<>();
        // 引取（CLAIM）は US16（IT7）で扱う。**出せない操作を選択肢に並べない**
        Arrays.stream(HandlingType.values())
                .filter(type -> type != HandlingType.CLAIM)
                .forEach(type -> labels.put(type.name(), type.displayName()));
        return labels;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("activities", queryService.findRecent(RECENT_LIMIT));
        return VIEW_LIST;
    }

    @GetMapping("/new")
    public String form(@ModelAttribute("handlingForm") HandlingForm form) {
        return VIEW_FORM;
    }

    /** 荷役作業を登録する。 */
    @PostMapping
    public String register(
            @Valid @ModelAttribute("handlingForm") HandlingForm form,
            BindingResult binding,
            Principal principal,
            RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        RegisterHandlingCommandService.Result result;
        try {
            result = registerService.register(new RegisterHandlingCommandService.Request(
                    form.getTrackingNumber(),
                    HandlingType.valueOf(form.getType()),
                    // 入力は業務のタイムゾーンの日時である。**UTC として読むと 9 時間ずれる**
                    form.getCompletionTime().atZone(businessZone()).toInstant(),
                    form.getLocationUnlocode(),
                    form.getVoyageNumber(),
                    actorName(form, principal)));
        } catch (ConcurrentModificationException e) {
            // 追跡・予約の更新で衝突した。**登録できたように見せない。**
            // フォームに留まり、入力し直せる場所を離れさせない
            binding.reject("handling.conflicted", e.getMessage());
            return VIEW_FORM;
        }

        switch (result.outcome()) {
            case NOT_FOUND, REJECTED -> {
                // **フォームに留まる。** 入力し直せる場所を離れさせない
                binding.reject("handling.rejected", result.reason());
                return VIEW_FORM;
            }
            default -> { /* 登録できたので下へ進む */ }
        }

        redirect.addFlashAttribute(FLASH_SUCCESS,
                "%s を登録しました（%s）".formatted(
                        HandlingType.valueOf(form.getType()).displayName(),
                        form.getLocationUnlocode()));
        // **予定と違っても登録はする。** 伝えるのは警告であり、拒否ではない
        if (result.validation() != null && result.validation().hasMessage()) {
            redirect.addFlashAttribute(FLASH_WARNING, result.validation().message());
        }
        return "redirect:/handling";
    }

    private ZoneId businessZone() {
        return clock.getZone();
    }

    private static String actorName(HandlingForm form, Principal principal) {
        if (form.getOperatorName() != null && !form.getOperatorName().isBlank()) {
            return form.getOperatorName();
        }
        return principal == null ? UNKNOWN_ACTOR : principal.getName();
    }
}
