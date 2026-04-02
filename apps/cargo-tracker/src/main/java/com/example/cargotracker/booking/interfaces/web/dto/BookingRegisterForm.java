package com.example.cargotracker.booking.interfaces.web.dto;

import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    private String unNumber;
    private String hazardClass;
    private BigDecimal minTempCelsius;
    private BigDecimal maxTempCelsius;

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
    public String getUnNumber() { return unNumber; }
    public void setUnNumber(String unNumber) { this.unNumber = unNumber; }
    public String getHazardClass() { return hazardClass; }
    public void setHazardClass(String hazardClass) { this.hazardClass = hazardClass; }
    public BigDecimal getMinTempCelsius() { return minTempCelsius; }
    public void setMinTempCelsius(BigDecimal minTempCelsius) { this.minTempCelsius = minTempCelsius; }
    public BigDecimal getMaxTempCelsius() { return maxTempCelsius; }
    public void setMaxTempCelsius(BigDecimal maxTempCelsius) { this.maxTempCelsius = maxTempCelsius; }
}
