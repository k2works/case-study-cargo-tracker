package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 予約を確定する（UC11 / US13）。営業が荷主の承認を確認してから使う。
 *
 * <p><b>通知していない予約は確定できない。</b> 荷主が知らないうちに確定すると、
 * 追跡番号の発行と輸送手配まで進んでしまう。承認は「通知した経路への承認」で
 * あって、通知が無ければ承認するものが無い。</p>
 *
 * @param confirmedBy 確定した営業担当者。あとで「誰が確定したか」を問われる
 */
public record ConfirmBookingCommand(
        @TargetEntityId String bookingId,
        String confirmedBy) {
}
