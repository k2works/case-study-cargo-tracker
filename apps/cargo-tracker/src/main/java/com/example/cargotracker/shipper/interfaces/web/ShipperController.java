package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 荷主の画面（US02）。
 *
 * <p>アクセスできるのは ROLE_SALES のみ（{@code SecurityConfig}）。
 * 登録成功時は PRG で詳細へリダイレクトする。
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/shippers")
public class ShipperController {

    private final RegisterShipperCommandService registerService;
    private final ShipperRepository repository;

    public ShipperController(
            RegisterShipperCommandService registerService, ShipperRepository repository) {
        this.registerService = registerService;
        this.repository = repository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("shippers", repository.findAll());
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
        var shipper = repository.findById(ShipperId.of(shipperId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "荷主が見つかりません"));
        model.addAttribute("shipper", shipper);
        return "shipper/detail";
    }
}
