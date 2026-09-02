package com.example.cargotracker.shared.contract.event;

/**
 * 荷主が登録された（契約イベント）。billingms が shipper_contract_snapshot に写す。
 *
 * <p>個人情報（name / email / phone / address）は荷主ごとの鍵で暗号化して載せる
 * （ADR-0003 crypto-shredding）。鍵を破棄したあとのリプレイでは復号できず null になるため、
 * 投影テーブルの該当列は NULL 許容にする。</p>
 */
public record ShipperRegisteredEvent(
        String shipperId,
        String shipperType,
        String name,
        String email,
        String phone,
        String address,
        String contractNumber,
        String discountRate) {
}
