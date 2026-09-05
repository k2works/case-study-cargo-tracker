package com.example.cargotracker.archfixture.crossbc.alpha.domain.model;

import com.example.cargotracker.archfixture.crossbc.beta.domain.model.BetaVoyageNumber;
import com.example.cargotracker.shared.domain.location.UnLocode;

/**
 * 別 BC の型を直接持つ集約。**これが通ると境界が消える。**
 *
 * <p>最小の違反例にしない。実コードで起きうる形（予約の集約が航海番号の型を
 * そのまま持ってしまう）で書く。共有カーネル（{@code UnLocode}）は許されるので、
 * 両方を持たせて「どちらで赤になったか」が分かるようにする。</p>
 */
public record AlphaCargo(UnLocode origin, BetaVoyageNumber assignedVoyage) {
}
