package com.example.bookingms.saga;

import com.example.bookingms.domain.commands.AssignTrackingDetailsCommand;
import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.model.Leg;
import com.example.shared.events.CargoTrackedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 予約ライフサイクル Saga（ADR-0009）。
 *
 * <p>予約登録から経路設計依頼・確定・追跡番号発行までを {@code bookingId} で関連付けて調整する。
 * IT3 では開始（{@link CargoBookedEvent}）・経路設計依頼（{@link RouteDesignRequestedEvent}）・
 * キャンセル終了（{@link BookingCancelledEvent}）までを実装する。
 * IT4 で経路確定（{@link CargoRoutedEvent}）による経路提案中フェーズへの遷移を追加する。</p>
 *
 * <p><b>IT5 タスク 1.2</b>：{@code BookingConfirmedEvent}（予約確定）を購読し、
 * shared モジュールの {@link TrackingIssuanceRequestedEvent} を {@link EventGateway} 経由で
 * Kafka に publish して trackingms に追跡初期化を依頼する。trackingms 側は
 * {@code InitializeTrackingCommand} で {@code TrackingActivity} を NOT_RECEIVED 初期化・採番し、
 * shared の {@code com.example.shared.events.CargoTrackedEvent} を Kafka 経由で bookingms に返す。
 * 本 Saga は {@code CargoTrackedEvent} を {@code @SagaEventHandler} で受信して予約状態を
 * TRACKING_ISSUED に更新し、{@code @EndSaga} で終了する（後続イテレーション）。
 * cross-service 受信ハンドラは ADR-0011（ホワイトリスト方式）に従い、
 * {@code AggregateNotFoundException} / {@code CommandExecutionException} の 2 種のみ WARN スキップする。</p>
 *
 * <p>追跡初期化に必要な属性（出発地・目的地・到着期限・貨物種別・確定旅程）は本 Saga が
 * 各 {@code @SagaEventHandler} で受信した情報を保持して集約する（{@code RouteDesignRequestedEvent}
 * + {@code CargoRoutedEvent}）。Saga はインスタンス間でシリアライズされるため、{@code EventGateway} は
 * {@code transient} で持ち、状態フィールドは全て Serializable な型に限定する。</p>
 */
@Saga
@ProcessingGroup("booking-saga")
public class BookingSagaManager {

    // Axon Saga は Serializable で永続化される。Gateway 系は Bean 注入のため
    // Saga 状態に含めず transient にする（フレームワーク要件、ADR-0009）。
    @SuppressWarnings("java:S2065")
    @Autowired
    private transient EventGateway eventGateway;

    @SuppressWarnings("java:S2065")
    @Autowired
    private transient CommandGateway commandGateway;

    @SuppressWarnings("unused") // Saga 状態として bookingId を保持する（関連付けの記録）
    private String bookingId;
    // 追跡初期化に必要な属性（RouteDesignRequestedEvent / CargoRoutedEvent から集約）
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private List<Leg> itinerary = new ArrayList<>();

    @StartSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(RouteDesignRequestedEvent event) {
        // 経路設計フェーズへ移行。追跡初期化に必要な属性を保持する（IT5 1.2）。
        this.bookingId = event.bookingId();
        this.originUnlocode = event.originUnlocode();
        this.destinationUnlocode = event.destinationUnlocode();
        this.arrivalDeadline = event.arrivalDeadline();
        this.cargoType = event.cargoType();
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(CargoRoutedEvent event) {
        // routingms の経路確定（RouteConfirmedEvent → AssignRouteToCargoCommand）を受けて
        // 経路提案中（ROUTE_PROPOSED）へ遷移。確定旅程を追跡初期化用に保持する（IT5 1.2）。
        this.bookingId = event.bookingId();
        this.itinerary = new ArrayList<>(event.legs());
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingConfirmedEvent event) {
        // 予約確定（CONFIRMED）。trackingms に追跡初期化を依頼するため
        // TrackingIssuanceRequestedEvent を cross-service publish する（IT5 1.2 完成）。
        // 受信側 trackingms は InitializeTrackingCommand を発行して TrackingActivity を
        // NOT_RECEIVED 初期化・採番する（IT5 1.3）。CargoTrackedEvent で本 Saga が終了する（IT5 1.4）。
        this.bookingId = event.bookingId();
        List<TrackingIssuanceRequestedEvent.LegData> legData = itinerary.stream()
                .map(leg -> new TrackingIssuanceRequestedEvent.LegData(
                        leg.voyageNumber(),
                        leg.loadUnlocode(),
                        leg.unloadUnlocode(),
                        leg.loadTime(),
                        leg.unloadTime()))
                .toList();
        eventGateway.publish(new TrackingIssuanceRequestedEvent(
                this.bookingId,
                this.originUnlocode,
                this.destinationUnlocode,
                this.arrivalDeadline,
                this.cargoType,
                legData
        ));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(CargoTrackedEvent event) {
        // trackingms から採番完了通知を受信。Cargo 集約に追跡情報を割り当てて
        // 予約状態を CONFIRMED → TRACKING_ISSUED に遷移し、Saga を終了する（IT5 1.4）。
        this.bookingId = event.bookingId();
        commandGateway.send(new AssignTrackingDetailsCommand(event.bookingId(), event.trackingNumber()));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingCancelledEvent event) {
        // 予約キャンセルにより Saga を終了する。
        this.bookingId = event.bookingId();
    }
}
