package com.example.cargotracker.quote.interfaces.web.dto;

import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommand;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積登録フォーム DTO。
 */
public class QuoteRegisterForm {

    @NotBlank(message = "出発地 (UN/LOCODE) は必須です")
    private String originLocode;

    @NotBlank(message = "目的地 (UN/LOCODE) は必須です")
    private String destinationLocode;

    @NotNull(message = "希望着日は必須です")
    private LocalDate requestedArrivalDate;

    @NotNull(message = "貨物種別は必須です")
    private CargoType cargoType;

    @NotNull(message = "重量は必須です")
    @DecimalMin(value = "0.01", message = "重量は 0 より大きくなければなりません")
    private BigDecimal weightKg;

    public RegisterQuoteCommand toCommand() {
        return new RegisterQuoteCommand(
                originLocode,
                destinationLocode,
                requestedArrivalDate,
                cargoType,
                weightKg
        );
    }

    public String getOriginLocode() { return originLocode; }
    public void setOriginLocode(String originLocode) { this.originLocode = originLocode; }
    public String getDestinationLocode() { return destinationLocode; }
    public void setDestinationLocode(String destinationLocode) { this.destinationLocode = destinationLocode; }
    public LocalDate getRequestedArrivalDate() { return requestedArrivalDate; }
    public void setRequestedArrivalDate(LocalDate requestedArrivalDate) { this.requestedArrivalDate = requestedArrivalDate; }
    public CargoType getCargoType() { return cargoType; }
    public void setCargoType(CargoType cargoType) { this.cargoType = cargoType; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
}
