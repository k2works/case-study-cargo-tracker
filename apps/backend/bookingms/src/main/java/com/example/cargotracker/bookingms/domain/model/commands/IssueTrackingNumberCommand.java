package com.example.cargotracker.bookingms.domain.model.commands;

/** 追跡番号発行コマンド（US14 / UC12）。 */
public record IssueTrackingNumberCommand(String bookingId) {}
