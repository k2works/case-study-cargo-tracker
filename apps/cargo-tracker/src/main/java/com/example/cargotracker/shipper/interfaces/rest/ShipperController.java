package com.example.cargotracker.shipper.interfaces.rest;

import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperRegisterForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shippers")
public class ShipperController {

    private static final String VIEW_REGISTER = "shipper/register";

    private final RegisterShipperCommandService registerShipperCommandService;

    public ShipperController(RegisterShipperCommandService registerShipperCommandService) {
        this.registerShipperCommandService = registerShipperCommandService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("shippers", registerShipperCommandService.findAll());
        return "shipper/list";
    }

    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new ShipperRegisterForm());
        return "shipper/register";
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
            return "redirect:/shippers";
        } catch (DuplicateShipperException e) {
            model.addAttribute("errorMessage",
                    "同一メールアドレスの荷主が既に登録されています（ID: " + e.getExistingShipperId() + "）");
            return VIEW_REGISTER;
        }
    }
}
