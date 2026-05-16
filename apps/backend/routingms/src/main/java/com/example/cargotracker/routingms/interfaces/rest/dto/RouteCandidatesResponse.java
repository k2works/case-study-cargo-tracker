package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.util.List;

public record RouteCandidatesResponse(List<CandidateItem> candidates) {

    public record CandidateItem(
            List<String> voyageNumbers,
            List<String> via,
            long transitDays
    ) {}
}
