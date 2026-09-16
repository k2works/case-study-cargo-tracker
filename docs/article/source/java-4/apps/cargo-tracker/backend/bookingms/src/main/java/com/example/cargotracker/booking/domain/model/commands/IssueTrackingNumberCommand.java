package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡番号を発行する（UC12 / US14）。<b>経路設計者の操作</b>（ui_design.md S22）。
 *
 * <p><b>採番は投影が行う。</b> 集約で MAX+1 を採ると、同時に 2 件発行したときに
 * 同じ番号が出る（{@code ShipperCode}・{@code BookingNumber} と同じ形）。集約は
 * 「発行してよいか」だけを判断し、番号は渡されたものを載せる。</p>
 *
 * @param trackingNumber 投影が採番した追跡番号
 */
public record IssueTrackingNumberCommand(
        @TargetEntityId String bookingId,
        String trackingNumber,
        String issuedBy) {
}
