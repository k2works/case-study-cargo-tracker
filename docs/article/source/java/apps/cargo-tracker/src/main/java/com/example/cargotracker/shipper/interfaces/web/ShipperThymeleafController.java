package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommand;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.interfaces.rest.dto.RegisterShipperRequest;
import com.example.cargotracker.shipper.interfaces.rest.transform.ShipperAssembler;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/shippers")
public class ShipperThymeleafController {

    private static final String SHIPPER_ATTRIBUTE = "shipper";
    private static final String NEW_VIEW = "shipper/new";

    private final RegisterShipperCommandService registerShipperCommandService;
    private final FindShipperQueryService findShipperQueryService;
    private final ShipperAssembler shipperAssembler;

    public ShipperThymeleafController(
            RegisterShipperCommandService registerShipperCommandService,
            FindShipperQueryService findShipperQueryService,
            ShipperAssembler shipperAssembler
    ) {
        this.registerShipperCommandService = registerShipperCommandService;
        this.findShipperQueryService = findShipperQueryService;
        this.shipperAssembler = shipperAssembler;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("shippers", findShipperQueryService.findAll().stream()
                .map(shipperAssembler::toResponse)
                .toList());
        return "shipper/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute(SHIPPER_ATTRIBUTE)) {
            model.addAttribute(SHIPPER_ATTRIBUTE, new RegisterShipperRequest());
        }
        return NEW_VIEW;
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute(SHIPPER_ATTRIBUTE) RegisterShipperRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return NEW_VIEW;
        }

        try {
            ShipperId shipperId = registerShipperCommandService.registerShipper(toCommand(request));
            redirectAttributes.addFlashAttribute("successMessage", "荷主を登録しました。");
            return "redirect:/shippers/" + shipperId;
        } catch (com.example.cargotracker.shipper.domain.model.exceptions.EmailAlreadyRegisteredException exception) {
            bindingResult.rejectValue("email", "duplicate", "このメールアドレスは既に登録されています。");
            return NEW_VIEW;
        }
    }

    @GetMapping("/{id}")
    public String show(@PathVariable String id, Model model) {
        model.addAttribute(SHIPPER_ATTRIBUTE, findShipperQueryService.findById(new ShipperId(UUID.fromString(id)))
                .map(shipperAssembler::toResponse)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
        return "shipper/show";
    }

    private RegisterShipperCommand toCommand(RegisterShipperRequest request) {
        return new RegisterShipperCommand(
                null,
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getAddress(),
                request.getShipperType(),
                request.getContractNumber(),
                request.getDiscountRate()
        );
    }
}
