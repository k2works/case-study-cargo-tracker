package com.example.cargotracker.shipper.interfaces.web;

import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommand;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
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

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/shippers")
public class ShipperThymeleafController {

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
        if (!model.containsAttribute("shipper")) {
            model.addAttribute("shipper", new RegisterShipperRequest());
        }
        return "shipper/new";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("shipper") RegisterShipperRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "shipper/new";
        }

        try {
            ShipperId shipperId = registerShipperCommandService.registerShipper(toCommand(request));
            return "redirect:/shippers/" + shipperId;
        } catch (IllegalStateException exception) {
            if (!"EMAIL_ALREADY_REGISTERED".equals(exception.getMessage())) {
                throw exception;
            }
            bindingResult.rejectValue("email", "duplicate", "このメールアドレスは既に登録されています。");
            return "shipper/new";
        }
    }

    @GetMapping("/{id}")
    public String show(@PathVariable String id, Model model) {
        model.addAttribute("shipper", findShipperQueryService.findById(new ShipperId(UUID.fromString(id)))
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
                request.getShipperType(),
                request.getContractNumber(),
                request.getDiscountRate()
        );
    }
}
