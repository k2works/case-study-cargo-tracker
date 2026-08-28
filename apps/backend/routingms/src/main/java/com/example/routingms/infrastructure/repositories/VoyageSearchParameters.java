package com.example.routingms.infrastructure.repositories;

import java.time.Instant;

/**
 * 検索条件を SQL へ渡すための形。
 *
 * <p>ドメインの列挙型のまま渡すと、MyBatis が列の文字列とどう突き合わせるかが
 * マッパー側の暗黙の了解になる。境界で名前に変えて渡す。
 */
public record VoyageSearchParameters(
        String originUnLocode,
        String destinationUnLocode,
        Instant departureFrom,
        Instant departureTo,
        String cargoType) {
}
