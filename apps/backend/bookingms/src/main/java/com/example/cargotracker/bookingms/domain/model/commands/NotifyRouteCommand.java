package com.example.cargotracker.bookingms.domain.model.commands;

/** 荷主への経路通知コマンド（US12 / UC10）。IT4 はログ記録のみ。 */
public record NotifyRouteCommand(String bookingId) {}
