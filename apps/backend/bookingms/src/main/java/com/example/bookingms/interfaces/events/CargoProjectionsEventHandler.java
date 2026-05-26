package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import com.example.bookingms.domain.model.HazardInfo;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.TemperatureCondition;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoLegMapper;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 貨物予約 Read Model 更新用の EventHandler（US04 + US05 + US11）。
 *
 * <p>{@link CargoBookedEvent} を受信して {@code cargo_summary} に 1 行 INSERT する。
 * US05 で hazard_* / temperature_* カラムへの書き込みを追加。
 * US11 で {@link CargoRoutedEvent} を受信し、状態更新と {@code cargo_leg} の確定を行う。</p>
 */
@Component
public class CargoProjectionsEventHandler {

    private final CargoSummaryMapper cargoSummaryMapper;
    private final CargoLegMapper cargoLegMapper;

    public CargoProjectionsEventHandler(CargoSummaryMapper cargoSummaryMapper,
                                        CargoLegMapper cargoLegMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
        this.cargoLegMapper = cargoLegMapper;
    }

    @EventHandler
    public void on(CargoBookedEvent event) {
        HazardInfo hazard = event.cargoSpec().hazardInfo();
        TemperatureCondition temp = event.cargoSpec().temperatureCondition();
        cargoSummaryMapper.insertCargoSummary(
                event.bookingId(),
                event.shipperId(),
                event.routeSpec().originUnlocode(),
                event.routeSpec().destinationUnlocode(),
                event.routeSpec().arrivalDeadline(),
                event.cargoSpec().cargoType().name(),
                event.cargoSpec().weightKg(),
                event.cargoSpec().dimensions().lengthCm(),
                event.cargoSpec().dimensions().widthCm(),
                event.cargoSpec().dimensions().heightCm(),
                event.cargoSpec().quantity(),
                event.cargoSpec().productName(),
                hazard == null ? null : hazard.imoClass(),
                hazard == null ? null : hazard.unNumber(),
                hazard == null ? null : hazard.declaration(),
                temp == null ? null : temp.minCelsius(),
                temp == null ? null : temp.maxCelsius(),
                event.bookingStatus(),
                event.routingStatus()
        );
    }

    @EventHandler
    public void on(RouteDesignRequestedEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), event.bookingStatus());
    }

    @EventHandler
    public void on(BookingConfirmedEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), event.bookingStatus());
    }

    @EventHandler
    public void on(BookingCancelledEvent event) {
        cargoSummaryMapper.updateBookingStatus(event.bookingId(), event.bookingStatus());
    }

    @EventHandler
    public void on(CargoRoutedEvent event) {
        cargoSummaryMapper.updateRouting(
                event.bookingId(), event.bookingStatus(), event.routingStatus());
        // 旅程は booking_id 単位で再確定する（tracking 再処理に備え冪等）
        cargoLegMapper.deleteByBookingId(event.bookingId());
        List<Leg> legs = event.legs();
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            cargoLegMapper.insert(
                    event.bookingId(),
                    i + 1,
                    leg.voyageNumber(),
                    leg.loadUnlocode(),
                    leg.unloadUnlocode(),
                    leg.loadTime(),
                    leg.unloadTime());
        }
    }
}
