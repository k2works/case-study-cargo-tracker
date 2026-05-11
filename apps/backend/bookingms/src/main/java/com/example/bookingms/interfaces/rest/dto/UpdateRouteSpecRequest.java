package com.example.bookingms.interfaces.rest.dto;

import java.time.LocalDate;

/**
 * 経路条件再設定リクエスト
 */
public record UpdateRouteSpecRequest(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {}
