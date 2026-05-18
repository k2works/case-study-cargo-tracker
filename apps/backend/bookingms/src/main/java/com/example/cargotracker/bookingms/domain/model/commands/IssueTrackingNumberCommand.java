package com.example.cargotracker.bookingms.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/** 追跡番号発行コマンド（US14 / UC12）。 */
public record IssueTrackingNumberCommand(@TargetEntityId String bookingId) {}
