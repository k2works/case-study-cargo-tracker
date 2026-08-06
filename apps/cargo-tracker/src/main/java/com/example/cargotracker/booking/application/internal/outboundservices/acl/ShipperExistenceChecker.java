package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.shared.domain.model.ShipperId;

/**
 * 荷主の存在確認（Booking → Shipper の ACL ポート）。
 *
 * <p>ビジネスルール 9（{@code domain-model.md}）: Booking Context は Shipper Context に
 * 直接依存せず、本ポートを通じて荷主の存在を確認する。ArchUnit ルール 4 が
 * この境界を固定しており、{@code booking} から {@code shipper} のクラスを直接参照すると
 * ビルドが落ちる。
 *
 * <p><strong>本ポートが返すのは「存在するか」だけである。</strong> 荷主の名称や
 * 契約割引率を返し始めると、Booking が Shipper のモデルを知ることになり、
 * ACL を挟んだ意味が失われる。表示用の荷主名は Booking のクエリ側が
 * 読み取り専用の SQL で取得する（CQRS のクエリ側）。
 */
public interface ShipperExistenceChecker {

    /**
     * 荷主が存在するか。
     *
     * @param shipperId 荷主 ID
     * @return 存在すれば {@code true}
     */
    boolean exists(ShipperId shipperId);

    /**
     * 荷主コードから荷主 ID を引く。
     *
     * <p>予約登録は荷主コード（{@code SHP-999999}）で荷主を指定する。**UUID の
     * 荷主 ID を覚えている利用者はいない。** 返すのは識別子だけであり、
     * 荷主の名称や割引率は境界の外に出さない。
     *
     * @return 該当する荷主の ID。存在しなければ空
     */
    java.util.Optional<ShipperId> findIdByShipperCode(String shipperCode);
}
