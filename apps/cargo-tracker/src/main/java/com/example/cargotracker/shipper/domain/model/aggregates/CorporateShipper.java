package com.example.cargotracker.shipper.domain.model.aggregates;

import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;

public final class CorporateShipper extends Shipper {

    private final ContractNumber contractNumber;
    private final DiscountRate discountRate;

    public CorporateShipper(
            ShipperId id,
            ShipperCode code,
            ShipperName name,
            Email email,
            Phone phone,
            ContractNumber contractNumber,
            DiscountRate discountRate
    ) {
        super(id, code, name, email, phone, ShipperType.CORPORATE);
        if (contractNumber == null) {
            throw new IllegalArgumentException("contractNumber must not be null");
        }
        if (discountRate == null) {
            throw new IllegalArgumentException("discountRate must not be null");
        }
        this.contractNumber = contractNumber;
        this.discountRate = discountRate;
    }

    public ContractNumber getContractNumber() {
        return contractNumber;
    }

    public DiscountRate getDiscountRate() {
        return discountRate;
    }
}
