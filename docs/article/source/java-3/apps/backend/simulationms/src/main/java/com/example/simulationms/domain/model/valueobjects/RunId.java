package com.example.simulationms.domain.model.valueobjects;

import java.util.regex.Pattern;

/**
 * 実行の識別子。
 *
 * <p>形は {@code SIM-YYYYMMDD-NNNN}。実行の一覧と、生成した業務データの追跡に使う。
 */
public record RunId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^SIM-\\d{8}-\\d{4}$");

    public RunId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("実行 ID の形が違います: " + value);
        }
    }

    public static RunId of(String value) {
        return new RunId(value);
    }
}
