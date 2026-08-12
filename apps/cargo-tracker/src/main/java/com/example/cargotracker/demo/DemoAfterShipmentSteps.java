package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;
import static com.example.cargotracker.demo.DemoActors.require;

import com.example.cargotracker.billing.application.internal.commandservices.CalculateChargeCommandService;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.tracking.application.internal.commandservices
        .RaiseTrackingExceptionCommandService;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 出発したあとに起きること —— 例外の起票（マニュアル 07.4）と請求（マニュアル 11）。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoAfterShipmentSteps {

    private final RaiseTrackingExceptionCommandService raiseException;
    private final CalculateChargeCommandService charge;
    private final Clock clock;

    DemoAfterShipmentSteps(
            RaiseTrackingExceptionCommandService raiseException,
            CalculateChargeCommandService charge,
            Clock clock) {
        this.raiseException = raiseException;
        this.charge = charge;
        this.clock = clock;
    }

    void raise(String trackingNumber, ExceptionType type, String location, String description) {
        var result = raiseException.raise(
                trackingNumber, type, location, clock.instant(), description, ACTOR);
        // **`exceptionId` では判定しない。** 受け付けたときも `null` が返る
        // （`declare` も同じ形である）。判定できるのは outcome だけである
        require(result.outcome() == RaiseTrackingExceptionCommandService.Outcome.ACCEPTED,
                "例外を起票できませんでした: " + result.reason());
    }

    void calculateCharge(BookingId id) {
        var calculated = charge.calculate(id.value().toString(), ACTOR);
        require(calculated.outcome() == CalculateChargeCommandService.Outcome.SUCCEEDED,
                "料金を算出できませんでした: " + calculated.reason());
    }
}
