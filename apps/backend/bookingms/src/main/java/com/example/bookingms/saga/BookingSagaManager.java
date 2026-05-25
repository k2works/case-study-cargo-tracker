package com.example.bookingms.saga;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
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
 * 経路確定（CargoRoutedEvent / IT4）と追跡番号発行（CargoTrackedEvent / IT5）に伴う
 * routingms・trackingms への cross-service 指示は後続イテレーションで追加する。</p>
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

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingCancelledEvent event) {
        // 予約キャンセルにより Saga を終了する。
        this.bookingId = event.bookingId();
    }
}
