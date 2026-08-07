package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shared.application.paging.PageLinks;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.commandservices.UpdateShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperView;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 荷主の画面（US02 / US32）。
 *
 * <p>アクセスできるのは ROLE_SALES のみ（{@code SecurityConfig}）。
 * 登録・訂正の成功時は PRG で詳細へリダイレクトする。
 *
 * <p>読み取りは {@link ShipperQueryService} を経由する。**Controller が
 * リポジトリを直接呼ぶと集約を 1 件ずつ読む実装が自然に生まれる**ため、
 * ArchUnit ルールで禁じている。
 */
@Controller
@RequestMapping("/shippers")
public class ShipperController {

    private static final String VIEW_LIST = "shipper/list";
    private static final String VIEW_FORM = "shipper/form";
    private static final String VIEW_DETAIL = "shipper/detail";
    private static final String VIEW_EDIT = "shipper/edit";
    private static final String ATTR_SHIPPER = "shipper";
    private static final String PARAM_KEYWORD = "keyword";
    private static final String REDIRECT_DETAIL = "redirect:/shippers/";

    private final RegisterShipperCommandService registerService;
    private final UpdateShipperCommandService updateService;
    private final ShipperQueryService queryService;

    public ShipperController(
            RegisterShipperCommandService registerService,
            UpdateShipperCommandService updateService,
            ShipperQueryService queryService) {
        this.registerService = registerService;
        this.updateService = updateService;
        this.queryService = queryService;
    }

    /** 一覧。キーワードで荷主名・荷主コード・メールアドレスを絞り込む（IT1 持ち越し C3）。 */
    @GetMapping
    public String list(
            @RequestParam(name = PARAM_KEYWORD, required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {
        model.addAttribute("shippers", queryService.search(keyword, PageRequest.of(page)));
        model.addAttribute(PARAM_KEYWORD, keyword == null ? "" : keyword);
        model.addAttribute("query", new PageLinks().with(PARAM_KEYWORD, keyword).queryPrefix());
        return VIEW_LIST;
    }

    /**
     * 荷主選択のモーダルに差し込む一覧（htmx の部分更新）。
     *
     * <p>貨物予約登録から荷主コードを調べるために開く。**別タブで一覧を開かせると
     * 画面を往復することになり、荷主コードを書き写す手間が残る**（`ui_design.md`）。
     *
     * <p>リテラルのパスは {@code /{shipperId}} より優先されるため、
     * 荷主 ID として解釈されることはない。
     */
    @GetMapping("/picker")
    public String picker(
            @RequestParam(name = PARAM_KEYWORD, required = false) String keyword, Model model) {
        model.addAttribute("shippers", queryService.search(keyword, PageRequest.of(1)));
        model.addAttribute(PARAM_KEYWORD, keyword == null ? "" : keyword);
        return "shipper/_picker :: rows";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ShipperForm());
        return VIEW_FORM;
    }

    @PostMapping
    public String register(
            @Valid @ModelAttribute("form") ShipperForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return VIEW_FORM;
        }

        var result = registerService.register(
                new ShipperName(form.getName()),
                new Email(form.getEmail()),
                new Phone(form.getPhone()),
                new Address(
                        form.getAddressCountry(), form.getAddressPostalCode(),
                        form.getAddressRegion(), form.getAddressCity(), form.getAddressStreet()));

        if (result.duplicated()) {
            // 登録せず既存を提示する。どちらを使うかは利用者が決める（US02）
            model.addAttribute("existingShipper", result.shipper());
            return VIEW_FORM;
        }

        redirect.addFlashAttribute(
                "flashSuccess", "荷主 " + result.shipper().shipperCode().value() + " を登録しました");
        return REDIRECT_DETAIL + result.shipper().id().value();
    }

    @GetMapping("/{shipperId}")
    public String detail(@PathVariable String shipperId, Model model) {
        model.addAttribute(ATTR_SHIPPER, findOrThrow(shipperId));
        return VIEW_DETAIL;
    }

    /** 訂正フォーム（US32）。 */
    @GetMapping("/{shipperId}/edit")
    public String editForm(@PathVariable String shipperId, Model model) {
        ShipperView shipper = findOrThrow(shipperId);
        model.addAttribute(ATTR_SHIPPER, shipper);
        model.addAttribute("form", toForm(shipper));
        return VIEW_EDIT;
    }

    /** 訂正（US32）。 */
    @PostMapping("/{shipperId}/edit")
    public String update(
            @PathVariable String shipperId,
            @Valid @ModelAttribute("form") ShipperEditForm form,
            BindingResult binding,
            Model model,
            Principal principal,
            RedirectAttributes redirect) {

        ShipperView shipper = findOrThrow(shipperId);
        if (binding.hasErrors()) {
            model.addAttribute(ATTR_SHIPPER, shipper);
            return VIEW_EDIT;
        }

        var result = updateService.update(
                ShipperId.of(shipperId),
                form.getVersion(),
                new ShipperName(form.getName()),
                new Email(form.getEmail()),
                new Phone(form.getPhone()),
                new Address(
                        form.getAddressCountry(), form.getAddressPostalCode(),
                        form.getAddressRegion(), form.getAddressCity(), form.getAddressStreet()),
                principal == null ? "unknown" : principal.getName());

        switch (result.outcome()) {
            case UPDATED -> {
                redirect.addFlashAttribute("flashSuccess", "荷主情報を訂正しました");
                return REDIRECT_DETAIL + shipperId;
            }
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "荷主が見つかりません");
            case DUPLICATED_EMAIL -> {
                // 訂正せず既存の荷主を提示する（US32 の受入基準）
                model.addAttribute("existingShipper", result.shipper());
                model.addAttribute(ATTR_SHIPPER, shipper);
                return VIEW_EDIT;
            }
            default -> {
                // 楽観的ロックの競合。**後勝ちで黙って上書きしない**
                model.addAttribute("conflicted", true);
                model.addAttribute(ATTR_SHIPPER, findOrThrow(shipperId));
                form.setVersion(result.shipper().version());
                return VIEW_EDIT;
            }
        }
    }

    private ShipperView findOrThrow(String shipperId) {
        return queryService.findById(shipperId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "荷主が見つかりません"));
    }

    private static ShipperEditForm toForm(ShipperView shipper) {
        ShipperEditForm form = new ShipperEditForm();
        form.setVersion(shipper.version());
        form.setName(shipper.name());
        form.setEmail(shipper.email());
        form.setPhone(shipper.phone());
        form.setAddressCountry(shipper.addressCountry());
        form.setAddressPostalCode(shipper.addressPostalCode());
        form.setAddressRegion(shipper.addressRegion());
        form.setAddressCity(shipper.addressCity());
        form.setAddressStreet(shipper.addressStreet());
        return form;
    }
}
