package com.example.cargotracker.shipper.domain.model.aggregates;

import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;

public final class CorporateShipper extends Shipper {

    private final ContractNumber contractNumber;
    private final DiscountRate discountRate;

    public CorporateShipper(
            Shipper shipper,
            ContractNumber contractNumber,
            DiscountRate discountRate
    ) {
        super(
                shipper.getId(),
                shipper.getCode(),
                shipper.getName(),
                shipper.getEmail(),
                shipper.getPhone(),
                shipper.getAddress(),
                ShipperType.CORPORATE
        );
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
