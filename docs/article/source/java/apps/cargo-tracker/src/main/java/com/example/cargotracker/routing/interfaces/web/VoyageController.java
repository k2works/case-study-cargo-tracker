package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/voyages")
public class VoyageController {

    private final VoyageQueryService voyageQueryService;

    public VoyageController(VoyageQueryService voyageQueryService) {
        this.voyageQueryService = voyageQueryService;
    }

    @GetMapping
    public String index(
            @RequestParam(required = false) String originUnlocode,
            @RequestParam(required = false) String destinationUnlocode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDeadline,
            Model model
    ) {
        boolean hasSearchParam = (originUnlocode != null && !originUnlocode.isBlank())
                || (destinationUnlocode != null && !destinationUnlocode.isBlank())
                || fromDate != null
                || arrivalDeadline != null;

        var voyages = hasSearchParam
                ? voyageQueryService.searchVoyages(originUnlocode, destinationUnlocode, fromDate, arrivalDeadline)
                : voyageQueryService.findAll();

        model.addAttribute("voyages", voyages);
        return "voyage/index";
    }
}
