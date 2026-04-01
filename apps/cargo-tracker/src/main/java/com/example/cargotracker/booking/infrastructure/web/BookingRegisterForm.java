package com.example.cargotracker.booking.infrastructure.web;

import com.example.cargotracker.booking.application.command.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.CargoType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class BookingRegisterForm {

    @NotBlank(message = "荷主 ID は必須です")
    private String shipperId;

    @NotNull(message = "貨物種別は必須です")
    private CargoType cargoType;

    @NotNull(message = "重量は必須です")
    @DecimalMin(value = "0.01", message = "重量は 0 より大きくなければなりません")
    private BigDecimal weightKg;

    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;

    @Min(value = 1, message = "個数は 1 以上でなければなりません")
    private int quantity = 1;

    private String description;

    @NotBlank(message = "出発地は必須です")
    private String originLocation;

    @NotBlank(message = "目的地は必須です")
    private String destinationLocation;

    @NotNull(message = "希望引渡日は必須です")
    private LocalDate requestedPickupDate;

    @NotNull(message = "希望着日は必須です")
    private LocalDate requestedDeliveryDate;

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
                requestedDeliveryDate
        );
    }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }
    public CargoType getCargoType() { return cargoType; }
    public void setCargoType(CargoType cargoType) { this.cargoType = cargoType; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOriginLocation() { return originLocation; }
    public void setOriginLocation(String originLocation) { this.originLocation = originLocation; }
    public String getDestinationLocation() { return destinationLocation; }
    public void setDestinationLocation(String destinationLocation) { this.destinationLocation = destinationLocation; }
    public LocalDate getRequestedPickupDate() { return requestedPickupDate; }
    public void setRequestedPickupDate(LocalDate requestedPickupDate) { this.requestedPickupDate = requestedPickupDate; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(LocalDate requestedDeliveryDate) { this.requestedDeliveryDate = requestedDeliveryDate; }
}
