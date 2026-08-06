package com.example.cargotracker.estimation.interfaces.web;

import com.example.cargotracker.estimation.application.internal.commandservices.CreateEstimateCommand;
import com.example.cargotracker.estimation.application.internal.commandservices.EstimateCommandService;
import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/estimates")
public class EstimationController {

    private final EstimateCommandService estimateCommandService;

    public EstimationController(EstimateCommandService estimateCommandService) {
        this.estimateCommandService = estimateCommandService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("estimates", estimateCommandService.findAll());
        return "estimation/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("estimateForm", new EstimateForm());
        return "estimation/new";
    }

    @PostMapping
    public String create(
            @Valid EstimateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "estimation/new";
        }

        EstimateId estimateId = estimateCommandService.createEstimate(new CreateEstimateCommand(
                form.getOriginUnlocode(),
                form.getDestinationUnlocode(),
                form.getArrivalDeadline(),
                form.getCargoType(),
                form.getWeightKg()
        ));

        redirectAttributes.addFlashAttribute("successMessage",
                "見積を作成しました（" + estimateId + "）");
        return "redirect:/estimates/" + estimateId;
    }

    @GetMapping("/{estimateId}")
    public String show(@PathVariable String estimateId, Model model) {
        Estimate estimate = estimateCommandService.findByEstimateId(new EstimateId(UUID.fromString(estimateId)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("estimate", estimate);
        return "estimation/show";
    }
}
