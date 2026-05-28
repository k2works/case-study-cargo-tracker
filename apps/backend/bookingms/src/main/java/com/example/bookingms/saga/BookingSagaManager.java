package com.example.bookingms.saga;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;

/**
 * 予約ライフサイクル Saga（ADR-0009）。
 *
 * <p>予約登録から経路設計依頼・確定・追跡番号発行までを {@code bookingId} で関連付けて調整する。
 * IT3 では開始（{@link CargoBookedEvent}）・経路設計依頼（{@link RouteDesignRequestedEvent}）・
 * キャンセル終了（{@link BookingCancelledEvent}）までを実装する。
 * IT4 で経路確定（{@link CargoRoutedEvent}）による経路提案中フェーズへの遷移を追加する。
 * </p>
 *
 * <p><b>IT5 タスク 1.2 で追加予定の延伸</b>：{@code BookingConfirmedEvent}（予約確定）を購読し、
 * shared モジュールの {@code com.example.shared.events.TrackingIssuanceRequestedEvent}（追加済）を
 * 発行して trackingms に追跡初期化を依頼する。trackingms 側は {@code InitializeTrackingCommand} で
 * {@code TrackingActivity} を NOT_RECEIVED 初期化・採番し、shared の
 * {@code com.example.shared.events.CargoTrackedEvent}（追加済）を Kafka 経由で bookingms に返す。
 * 本 Saga は {@code CargoTrackedEvent} を {@code @SagaEventHandler} で受信して予約状態を
 * TRACKING_ISSUED に更新し、{@code @EndSaga} で終了する（iteration_plan-5.md §設計トピック・
 * §タスク 1.1〜1.4）。cross-service 受信ハンドラは ADR-0011（ホワイトリスト方式）に従い、
 * {@code AggregateNotFoundException} / {@code CommandExecutionException} の 2 種のみ WARN スキップする。</p>
 */
@Saga
public class BookingSagaManager {

    @SuppressWarnings("unused") // Saga 状態として bookingId を保持する（関連付けの記録）
    private String bookingId;

    @StartSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(RouteDesignRequestedEvent event) {
        // 経路設計フェーズへ移行。routingms への経路設計指示は IT4（経路候補算出）で実装する。
        this.bookingId = event.bookingId();
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(CargoRoutedEvent event) {
        // routingms の経路確定（RouteConfirmedEvent → AssignRouteToCargoCommand）を受けて
        // 経路提案中（ROUTE_PROPOSED）へ遷移。追跡番号発行（IT5）まで Saga は継続する。
        this.bookingId = event.bookingId();
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingConfirmedEvent event) {
        // 予約確定（CONFIRMED）。trackingms への追跡発行依頼フェーズへ移行する。
        // cross-service publish（shared TrackingIssuanceRequestedEvent の発行）は
        // IT5 タスク 1.2 後続の本格実装で追加する（EventGateway 注入または Cargo 集約経由）。
        // 現段階は Saga 状態を保持して継続することのみを担保する。
        this.bookingId = event.bookingId();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingCancelledEvent event) {
        // 予約キャンセルにより Saga を終了する。
        this.bookingId = event.bookingId();
    }
}
