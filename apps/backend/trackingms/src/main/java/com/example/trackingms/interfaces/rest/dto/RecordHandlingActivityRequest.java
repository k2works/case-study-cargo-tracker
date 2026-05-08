package com.example.trackingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 荷役作業記録リクエスト DTO
 */
public record RecordHandlingActivityRequest(
        @NotBlank String trackingNumber,
        @NotBlank String eventType,
        @NotBlank String locationUnlocode,
        @NotNull LocalDateTime eventTime,
        String voyageNumber
) {}
