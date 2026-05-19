package com.example.cargotracker.trackingms.interfaces.rest.dto;

/**
 * 追跡例外解決リクエスト DTO（US20）。
 */
public record ResolveTrackingExceptionRequest(
        String resolution,
        String operatorId) { }
