package com.example.cargotracker.archfixture.violating.domain.model;

import java.time.Clock;
import java.time.LocalDate;

/** 違反フィクスチャ: 業務の「今日」を UTC で決めている。 */
public class UtcClockService {

    public LocalDate today() {
        return LocalDate.now(Clock.systemUTC());
    }
}
