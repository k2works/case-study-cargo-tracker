package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.commandservices.AssignRouteUseCase;
import com.example.bookingms.application.internal.commandservices.BookCargoUseCase;
import com.example.bookingms.application.internal.commandservices.ConfirmBookingUseCase;
import com.example.bookingms.application.internal.commandservices.IssueTrackingNumberUseCase;
import com.example.bookingms.application.internal.commandservices.NotifyShipperUseCase;
import com.example.bookingms.application.internal.commandservices.RequestConsultationUseCase;
import com.example.bookingms.application.internal.commandservices.RequestRoutingUseCase;
import com.example.bookingms.application.internal.commandservices.ReviseBookingScheduleUseCase;
import com.example.bookingms.application.internal.commandservices.ReturnToRoutingUseCase;
import com.example.bookingms.application.internal.queryservices.SearchCargoUseCase;
import org.springframework.stereotype.Component;

/**
 * 貨物予約の入口が使うユースケースをまとめる。
 *
 * <p>予約は 1 つの画面から複数の操作（登録・検索・引き渡し・経路の割り当て・差し戻し）を
 * 行うため、入口が扱うユースケースが増え続ける。**引数の並びで受け取ると、足すたびに
 * コンストラクタが伸び、テストの組み立ても壊れる**。まとめて 1 つで受ける。
 *
 * <p>まとめるのは「入口が使うもの」であって、業務上の関係ではない。ユースケースどうしは
 * ここでも互いを知らない。
 */
@Component
public record BookingUseCases(
        BookCargoUseCase bookCargo,
        SearchCargoUseCase searchCargo,
        RequestRoutingUseCase requestRouting,
        AssignRouteUseCase assignRoute,
        RequestConsultationUseCase requestConsultation,
        NotifyShipperUseCase notifyShipper,
        ConfirmBookingUseCase confirmBooking,
        ReturnToRoutingUseCase returnToRouting,
        IssueTrackingNumberUseCase issueTrackingNumber,
        ReviseBookingScheduleUseCase reviseSchedule) {
}
