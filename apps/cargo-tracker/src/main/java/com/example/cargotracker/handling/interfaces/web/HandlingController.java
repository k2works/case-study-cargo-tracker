package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices.RegisterHandlingCommandService;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 * である。<strong>Handling は独立した境界付けられたコンテキストである</strong>
 * （ADR-010。ADR-002 を置き換えた）。
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
        // 引取（CLAIM）は US16（IT7）で正式に開いた（IT6 レビュー L1）。
        // それまでは選択肢に無いのに POST すれば通る状態だった
        Arrays.stream(HandlingType.values())
                .forEach(type -> labels.put(type.name(), type.displayName()));
        return labels;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("activities", queryService.findRecent(RECENT_LIMIT));
        return VIEW_LIST;
    }

    /**
     * 荷役作業登録フォームを開く。
     *
     * <p><strong>作業日時に「いま」を入れておく</strong>（IT6 レビュー H12）。
     * 荷役はほぼ常に作業した直後に登録される。既定値が空だと、
     * 現場は毎回「いま」を手で打ち込むことになる。**手袋をしたまま、
     * 屋外の日射しの下で日付を打つ**のは、それだけで登録をためらう理由になる。
     *
     * <p>業務のタイムゾーンで決める。JVM 既定の {@code now()} だと、
     * 実行環境が UTC のとき 9 時間ずれた既定値が入る。
     */
    @GetMapping("/new")
    public String form(@ModelAttribute("handlingForm") HandlingForm form) {
        if (form.getCompletionTime() == null) {
            form.setCompletionTime(
                    LocalDateTime.now(clock.withZone(businessZone()))
                            .truncatedTo(ChronoUnit.MINUTES));
        }
        return VIEW_FORM;
    }

    /**
     * 予定ルートから外れていれば確認画面へ回す（US28）。
     *
     * <p><strong>承認を挟むのは誤配のときだけ</strong>にする。毎回挟むと現場の作業が
     * 倍になり、警告そのものが読み飛ばされる。
     *
     * @return 確認画面・入力画面の名前。そのまま登録に進んでよければ {@code null}
     */
    private String confirmIfMisrouted(
            HandlingForm form, Principal principal, Model model, BindingResult binding) {
        if (form.isAcknowledged()) {
            return null;
        }
        try {
            var validation = registerService.validateOnly(toRequest(form, principal));
            if (validation.isPresent() && validation.get().isMisrouted()) {
                model.addAttribute("misrouteWarning", validation.get().message());
                return "handling/confirm";
            }
            return null;
        } catch (IllegalArgumentException e) {
            // **入力の誤りは登録と同じ形で返す**（航海番号の欠落など）。
            // ここで落とすと、同じ誤りが検証と登録で違う見え方になる
            binding.reject("handling.rejected", e.getMessage());
            return VIEW_FORM;
        }
    }

    /**
     * フォームの入力を登録の要求に組み立てる。
     *
     * <p><strong>1 か所に集める。</strong> 検証と登録で別々に組み立てると、
     * 「警告は出なかったのに登録したら誤配になった」形を作れてしまう。
     */
    private RegisterHandlingCommandService.Request toRequest(
            HandlingForm form, Principal principal) {
        return new RegisterHandlingCommandService.Request(
                form.getTrackingNumber(),
                HandlingType.valueOf(form.getType()),
                // 入力は業務のタイムゾーンの日時である。**UTC として読むと 9 時間ずれる**
                form.getCompletionTime().atZone(businessZone()).toInstant(),
                form.getLocationUnlocode(),
                form.getVoyageNumber(),
                form.getConfirmationCode(),
                form.getConsigneeName(),
                form.getNote(),
                actorName(form, principal));
    }

    /** 荷役作業を登録する。 */
    @PostMapping
    public String register(
            @Valid @ModelAttribute("handlingForm") HandlingForm form,
            BindingResult binding,
            Principal principal,
            Model model,
            RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        // **登録前に警告する**（US28）。予定ルートから外れた積込・荷降しは、
        // 記録してしまうと取り消す手段が無い（取り消しは US36 でまだ無い）。
        // **承認を挟むのは誤配のときだけ**にする。毎回挟むと現場の作業が倍になり、
        // 警告そのものが読み飛ばされる
        String beforeRegister = confirmIfMisrouted(form, principal, model, binding);
        if (beforeRegister != null) {
            return beforeRegister;
        }

        RegisterHandlingCommandService.Result result;
        try {
            result = registerService.register(toRequest(form, principal));
        } catch (IllegalArgumentException e) {
            // 引取確認の欠落など、種別ごとの必須項目の誤り。**業務のことばで返す**
            binding.reject("handling.rejected", e.getMessage());
            return VIEW_FORM;
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
