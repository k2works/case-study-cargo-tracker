package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.commandservices
        .RaiseTrackingExceptionCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingExceptionQueryService;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
 * 例外イベントの画面（US19 / US20。<strong>ROLE_TRACKER</strong>）。
 *
 * <p>エスカレーション中の一覧だけは管理者が見る（{@code /tracking/exceptions/escalated}）。
 * <strong>「送った」だけで誰も見ないなら、通知に意味は無い。</strong>
 * US20 の受入基準「管理職への escalation 通知」の受け皿である。
 *
 * <p><strong>PRG を使う。</strong> 起票も解決も副作用を持つため、再読込で
 * 二重に登録される形を作らない。
 */
@Controller
@RequestMapping("/tracking/exceptions")
public class TrackingExceptionController {

    private static final String ATTR_EXCEPTIONS = "exceptions";
    private static final String ATTR_EXCEPTION = "exception";
    private static final String ATTR_TYPES = "types";
    private static final String FLASH_ERROR = "flashError";
    private static final String FLASH_SUCCESS = "flashSuccess";

    private static final String VIEW_LIST = "tracking/exceptions";
    private static final String VIEW_FORM = "tracking/exception-form";
    private static final String VIEW_DETAIL = "tracking/exception-detail";
    private static final String REDIRECT_LIST = "redirect:/tracking/exceptions";

    private static final String NOT_FOUND_MESSAGE = "該当する例外が見つかりません。";

    private final TrackingExceptionQueryService queryService;
    private final RaiseTrackingExceptionCommandService commandService;

    /** **業務のタイムゾーンで日時を解釈する。** UTC で受けると時差の分だけずれる。 */
    private final Clock clock;

    public TrackingExceptionController(
            TrackingExceptionQueryService queryService,
            RaiseTrackingExceptionCommandService commandService,
            Clock clock) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.clock = clock;
    }

    /**
     * 例外イベント一覧（追跡管理者の作業入口）。
     *
     * <p><strong>既定は未解決だけを出す。</strong> この一覧は「連絡すべき仕事の
     * 待ち行列」であり、片づいた例外が混ざると、いま何をすべきかが読めない
     * （出港済みの便が航路一覧に混ざると一覧全体が信用されないのと同じ）。
     */
    @GetMapping
    public String list(
            @RequestParam(name = "resolved", defaultValue = "false") boolean includeResolved,
            Model model) {
        model.addAttribute(ATTR_EXCEPTIONS, queryService.search(!includeResolved, false));
        model.addAttribute("includeResolved", includeResolved);
        model.addAttribute("escalatedOnly", false);
        return VIEW_LIST;
    }

    /**
     * エスカレーション中の例外（<strong>管理者</strong>。US20 の受入基準の受け皿）。
     *
     * <p><strong>URL を分ける。</strong> 同じ一覧に絞り込みのパラメータを足すと、
     * 管理者に例外一覧そのものを開かせることになり、認可の対象が曖昧になる。
     */
    @GetMapping("/escalated")
    public String escalated(Model model) {
        model.addAttribute(ATTR_EXCEPTIONS, queryService.search(true, true));
        model.addAttribute("includeResolved", false);
        model.addAttribute("escalatedOnly", true);
        return VIEW_LIST;
    }

    /**
     * 例外の登録フォーム。
     *
     * <p>追跡詳細から開くときは追跡番号を埋める。
     * <strong>番号を手で書き写させない</strong>（写し間違えれば別の貨物に例外が付く）。
     */
    @GetMapping("/new")
    public String newForm(
            @RequestParam(name = "trackingNumber", required = false) String trackingNumber,
            Model model) {
        model.addAttribute("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        model.addAttribute(ATTR_TYPES, manuallyRaisableTypes());
        model.addAttribute("defaultOccurredAt", LocalDateTime.now(clock).withSecond(0).withNano(0));
        return VIEW_FORM;
    }

    /** 例外を登録する（US19 / US20）。 */
    @PostMapping
    public String raise(
            @RequestParam("trackingNumber") String trackingNumber,
            @RequestParam("exceptionType") String exceptionType,
            @RequestParam("location") String location,
            @RequestParam("occurredAt")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime occurredAt,
            @RequestParam(name = "description", required = false) String description,
            java.security.Principal principal,
            RedirectAttributes redirect) {

        ExceptionType type;
        try {
            type = ExceptionType.valueOf(exceptionType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不明な例外種別です");
        }

        var result = commandService.raise(
                trackingNumber, type, location,
                occurredAt.atZone(businessZone()).toInstant(), description,
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case NOT_FOUND -> {
                redirect.addFlashAttribute(FLASH_ERROR,
                        "追跡番号 %s の貨物が見つかりません。".formatted(trackingNumber));
                return "redirect:/tracking/exceptions/new";
            }
            case REJECTED, CONFLICTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return "redirect:/tracking/exceptions/new";
            }
            default -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "例外を登録しました。荷主への通知を記録しました");
        }
        return REDIRECT_LIST;
    }

    /** 例外の詳細と解決フォーム。 */
    @GetMapping("/{exceptionId}")
    public String detail(@PathVariable("exceptionId") long exceptionId, Model model) {
        var found = queryService.findById(exceptionId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        model.addAttribute(ATTR_EXCEPTION, found.get());
        return VIEW_DETAIL;
    }

    /** 例外を解決する（US19「対応内容を入力して荷主に対応報告を送信できる」）。 */
    @PostMapping("/{exceptionId}/resolve")
    public String resolve(
            @PathVariable("exceptionId") long exceptionId,
            @RequestParam("trackingNumber") String trackingNumber,
            @RequestParam(name = "resolutionNotes", required = false) String resolutionNotes,
            java.security.Principal principal,
            RedirectAttributes redirect) {

        var result = commandService.resolve(
                trackingNumber, exceptionId, resolutionNotes,
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
            case REJECTED, CONFLICTED -> {
                redirect.addFlashAttribute(FLASH_ERROR, result.reason());
                return "redirect:/tracking/exceptions/" + exceptionId;
            }
            default -> redirect.addFlashAttribute(FLASH_SUCCESS,
                    "例外に対応しました。荷主への対応報告を記録しました");
        }
        return REDIRECT_LIST;
    }

    /**
     * 画面で選べる種別。
     *
     * <p><strong>税関保留は出さない。</strong> どう起票するかが US29 で未決であり、
     * 選べる形にすると「選べるのに正しく使えない」項目が画面に残る。
     */
    private static List<TypeOption> manuallyRaisableTypes() {
        return ExceptionType.manuallyRaisable().stream()
                .map(type -> new TypeOption(type.name(), type.displayName(),
                        type.escalationRequired()))
                .toList();
    }

    /**
     * 画面に出す種別の選択肢。
     *
     * @param escalates エスカレーションが要るか。**選ぶ前に画面で知らせる**
     */
    public record TypeOption(String name, String label, boolean escalates) {
    }

    private ZoneId businessZone() {
        return clock.getZone();
    }
}
