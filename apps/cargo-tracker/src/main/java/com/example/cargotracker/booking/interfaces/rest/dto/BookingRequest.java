package com.example.cargotracker.booking.interfaces.rest.dto;

import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BookingRequest(
        @NotBlank(message = "荷主 ID は必須です")
        String shipperId,
        @NotNull(message = "貨物種別は必須です")
        CargoType cargoType,
        @NotNull(message = "重量は必須です")
        @DecimalMin(value = "0.01", message = "重量は 0 より大きくなければなりません")
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        @Min(value = 1, message = "個数は 1 以上でなければなりません")
        int quantity,
        String description,
        @NotBlank(message = "出発地は必須です")
        String originLocation,
        @NotBlank(message = "目的地は必須です")
        String destinationLocation,
        @NotNull(message = "希望引渡日は必須です")
        LocalDate requestedPickupDate,
        @NotNull(message = "希望着日は必須です")
        LocalDate requestedDeliveryDate,
        String unNumber,
        String hazardClass,
        BigDecimal minTempCelsius,
        BigDecimal maxTempCelsius
) {
    public RegisterBookingCommand toCommand() {
        return new RegisterBookingCommand(
                UUID.fromString(shipperId),
                cargoType,
                weightKg,
                lengthCm,
                widthCm,
                heightCm,
                quantity,
                description,
                originLocation,
                destinationLocation,
                requestedPickupDate,
                requestedDeliveryDate,
                unNumber,
                hazardClass,
                minTempCelsius,
                maxTempCelsius
        );
    }
}
