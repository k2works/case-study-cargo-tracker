package com.example.cargotracker.shipper.interfaces.rest;

import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperRegisterForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/shippers")
public class ShipperController {

    private final RegisterShipperCommandService registerShipperCommandService;
    private final ShipperRepository shipperRepository;

    public ShipperController(RegisterShipperCommandService registerShipperCommandService,
                              ShipperRepository shipperRepository) {
        this.registerShipperCommandService = registerShipperCommandService;
        this.shipperRepository = shipperRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("shippers", List.of());
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
            return "shipper/register";
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
            ShipperId shipperId = registerShipperCommandService.execute(command);
            redirectAttributes.addFlashAttribute("successMessage",
                    "荷主を登録しました（ID: " + shipperId + "）");
            return "redirect:/shippers";
        } catch (DuplicateShipperException e) {
            model.addAttribute("errorMessage",
                    "同一メールアドレスの荷主が既に登録されています（ID: " + e.getExistingShipperId() + "）");
            return "shipper/register";
        }
    }
}
