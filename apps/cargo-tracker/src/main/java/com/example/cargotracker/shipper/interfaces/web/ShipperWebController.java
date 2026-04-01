package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.interfaces.web.dto.ShipperRegisterForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shippers")
public class ShipperWebController {

    private static final String VIEW_REGISTER = "shipper/register";

    private final RegisterShipperCommandService registerShipperCommandService;
    private final FindShipperQueryService findShipperQueryService;

    public ShipperWebController(RegisterShipperCommandService registerShipperCommandService,
                                FindShipperQueryService findShipperQueryService) {
        this.registerShipperCommandService = registerShipperCommandService;
        this.findShipperQueryService = findShipperQueryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("shippers", findShipperQueryService.findAll());
        return "shipper/list";
    }

    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new ShipperRegisterForm());
        return VIEW_REGISTER;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") ShipperRegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            return VIEW_REGISTER;
        }

        try {
            RegisterShipperCommand command = new RegisterShipperCommand(
                    form.getName(),
                    form.getEmail(),
                    form.getPhone(),
                    CustomerCategory.valueOf(form.getCategory()),
                    form.getContractNumber(),
                    form.getDiscountRate()
            );
            var shipperId = registerShipperCommandService.execute(command);
            redirectAttributes.addFlashAttribute("successMessage",
                    "荷主を登録しました（ID: " + shipperId + "）");
            redirectAttributes.addFlashAttribute("createdShipperId", shipperId.toString());
            redirectAttributes.addFlashAttribute("createdShipperName", form.getName());
            return "redirect:/shippers";
        } catch (DuplicateShipperException e) {
            model.addAttribute("errorMessage",
                    "同一メールアドレスの荷主が既に登録されています（ID: " + e.getExistingShipperId() + "）");
            return VIEW_REGISTER;
        }
    }
}
