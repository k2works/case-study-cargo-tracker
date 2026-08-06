package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.ShipperId;

/**
 * 貨物予約の登録コマンド（遷移表 #1。US04）。
 *
 * <p>荷主の<strong>存在</strong>は本コマンドでは検証しない。BC をまたぐ確認であり、
 * ドメインモデルから他 BC を参照することはできない（ADR-005 / ADR-007）。
 * 存在確認は {@code ShipperExistenceChecker} ACL ポートを通じてコマンドサービスが行う。
 *
 * @param shipperId          荷主 ID（必須）
 * @param cargoSpecification 貨物仕様（必須）
 * @param routeSpecification ルート仕様（必須）
 */
public record BookCargoCommand(
        ShipperId shipperId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification) {}
