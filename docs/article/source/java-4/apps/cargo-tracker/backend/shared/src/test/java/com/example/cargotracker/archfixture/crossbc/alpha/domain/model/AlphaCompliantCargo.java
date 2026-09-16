package com.example.cargotracker.archfixture.crossbc.alpha.domain.model;

import com.example.cargotracker.shared.domain.location.UnLocode;

/** 共有カーネルだけを使う集約。こちらは赤にならない。 */
public record AlphaCompliantCargo(UnLocode origin, String assignedVoyageNumber) {
}
