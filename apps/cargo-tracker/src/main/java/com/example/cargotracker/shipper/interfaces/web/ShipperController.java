package com.example.cargotracker.shipper.interfaces.web;

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
            @RequestParam(name = "keyword", required = false) String keyword, Model model) {
        model.addAttribute("shippers", queryService.search(keyword));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        return "shipper/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ShipperForm());
        return "shipper/form";
    }

    @PostMapping
    public String register(
            @Valid @ModelAttribute("form") ShipperForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "shipper/form";
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
            return "shipper/form";
        }

        redirect.addFlashAttribute(
                "flashSuccess", "荷主 " + result.shipper().shipperCode().value() + " を登録しました");
        return "redirect:/shippers/" + result.shipper().id().value();
    }

    @GetMapping("/{shipperId}")
    public String detail(@PathVariable String shipperId, Model model) {
        model.addAttribute("shipper", 見つける(shipperId));
        return "shipper/detail";
    }

    /** 訂正フォーム（US32）。 */
    @GetMapping("/{shipperId}/edit")
    public String editForm(@PathVariable String shipperId, Model model) {
        ShipperView shipper = 見つける(shipperId);
        model.addAttribute("shipper", shipper);
        model.addAttribute("form", toForm(shipper));
        return "shipper/edit";
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

        ShipperView shipper = 見つける(shipperId);
        if (binding.hasErrors()) {
            model.addAttribute("shipper", shipper);
            return "shipper/edit";
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
                return "redirect:/shippers/" + shipperId;
            }
            case NOT_FOUND -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "荷主が見つかりません");
            case DUPLICATED_EMAIL -> {
                // 訂正せず既存の荷主を提示する（US32 の受入基準）
                model.addAttribute("existingShipper", result.shipper());
                model.addAttribute("shipper", shipper);
                return "shipper/edit";
            }
            default -> {
                // 楽観的ロックの競合。**後勝ちで黙って上書きしない**
                model.addAttribute("conflicted", true);
                model.addAttribute("shipper", 見つける(shipperId));
                form.setVersion(result.shipper().version());
                return "shipper/edit";
            }
        }
    }

    private ShipperView 見つける(String shipperId) {
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
