package com.example.cargotracker.estimation.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積の条件（何をどこからどこへ、いつまでに）。
 *
 * <p><strong>5 つの値を一列に並べない</strong>（IT17 の R6）。出発地と目的地は
 * どちらも {@code Location} であり、取り違えても<strong>コンパイルは通り、
 * 航路が逆に出るだけ</strong>である。
 *
 * @param origin          出発地
 * @param destination     目的地
 * @param arrivalDeadline 希望到着期限
 * @param cargoType       貨物種別
 * @param weightKg        重量（kg）
 */
public record EstimateSpecification(
        Location origin,
        Location destination,
        LocalDate arrivalDeadline,
        EstimationCargoType cargoType,
        BigDecimal weightKg) { }
