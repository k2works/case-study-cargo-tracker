package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.util.List;

public record AdjustRouteResponse(
        List<RouteCandidatesResponse.CandidateItem> candidates,
        String noRoutesMessage
) {}
