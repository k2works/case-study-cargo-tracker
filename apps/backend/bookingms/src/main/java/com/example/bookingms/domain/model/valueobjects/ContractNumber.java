package com.example.bookingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 法人契約の番号。
 *
 * <p>形式は契約先ごとに異なるため検査しない。守るのは「空でないこと」と、
 * 打ち込みの前後の空白で別の契約に見えないことだけ。
 */
public final class ContractNumber {

    private final String value;

    private ContractNumber(String value) {
        this.value = value;
    }

    public static ContractNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("契約番号は必須です");
        }
        return new ContractNumber(value.trim());
    }

    /** 永続化された行から戻す。検査しない（{@link Dimensions#restore} と同じ理由）。 */
    public static ContractNumber restore(String value) {
        return new ContractNumber(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContractNumber contractNumber && value.equals(contractNumber.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
