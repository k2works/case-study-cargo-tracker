package com.example.cargotracker.shared.contract.event;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷主が登録された（契約イベント）。billingms が shipper_contract_snapshot に写す。
 *
 * <p>個人情報（name / email / phone / address）は荷主ごとの鍵で暗号化して載せる
 * （ADR-0003 crypto-shredding）。鍵を破棄したあとのリプレイでは復号できず null になるため、
 * 投影テーブルの該当列は NULL 許容にする。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> DCB はイベントに付いたタグで集約を復元する。
 * {@code @EventSourced(tagKey)} は集約側の宣言でしかなく、イベント側で「どの項目が
 * そのタグか」を言わないとタグが書かれない。付け忘れると {@code Shipper} は毎回
 * <b>空のまま復元され</b>、復元した状態を見る守りが丸ごと素通りする
 * （[ADR-0001] 決定 5 第 8 項）。</p>
 */
public record ShipperRegisteredEvent(
        @EventTag(key = "shipperId") String shipperId,
        String shipperType,
        String name,
        String email,
        String phone,
        String address,
        String contractNumber,
        String discountRate,
        // シミュレーション由来か（US33 §受入基準 3 / [ADR-0020]）。
        //
        // **この項目より前に積まれたイベントでは false になる**——既存の荷主は
        // 本物なので、既定値が業務上正しい（`golden-legacy` が読めることを固定）。
        //
        // **荷主種別にしない。** 由来と荷主種別は直交する（シミュレーションでも
        // 法人割引を踏みたい）し、`ShipperType` は割引の判断に使われている。
        //
        // **印だけでは混ざらない。** 除外は各 BC の読み口の側に置く。
        // **プリミティブにしない。** `boolean` だと項目が無い古いイベントを
        // 読めない（IT16 で実測。「既定値 false で読める」という見立ては誤りだった）。
        // `null` は「印が付く前に登録された荷主」＝本物である。
        Boolean simulated) {
}
