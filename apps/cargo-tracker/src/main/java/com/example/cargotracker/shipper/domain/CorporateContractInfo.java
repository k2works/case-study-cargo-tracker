package com.example.cargotracker.shipper.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class CorporateContractInfo {

    private final String contractNumber;
    private final BigDecimal discountRate;

    public CorporateContractInfo(String contractNumber, BigDecimal discountRate) {
        if (contractNumber == null || contractNumber.isBlank()) {
            throw new IllegalArgumentException("契約番号は必須です");
        }
        if (discountRate == null) {
            throw new IllegalArgumentException("割引率は必須です");
        }
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(new BigDecimal("30")) > 0) {
            throw new IllegalArgumentException("割引率は 0〜30% の範囲で設定してください");
        }
        this.contractNumber = contractNumber;
        this.discountRate = discountRate;
    }

    public String contractNumber() {
        return contractNumber;
    }

    public BigDecimal discountRate() {
        return discountRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CorporateContractInfo that)) return false;
        return Objects.equals(contractNumber, that.contractNumber)
                && Objects.equals(discountRate, that.discountRate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractNumber, discountRate);
    }
}
