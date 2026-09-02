package com.example.cargotracker.booking.application.port;

import java.util.Optional;

/**
 * 荷主ごとの暗号鍵を扱うポート（ADR-0003）。
 *
 * <p>鍵は {@code shipperId} から決定的に引く。{@code shipper_id → key_id} の対応表は
 * 持たない。対応表は「削除したはずの荷主の痕跡」そのもので、それ自体が個人情報の残存に
 * なるためである。</p>
 *
 * <p>本番は AWS KMS のエイリアス {@code alias/cargo-tracker/shipper/<shipperId>}、
 * ローカルと CI はファイルの実装を当てる。</p>
 */
public interface ShipperKeyRepository {

    /** 鍵の参照名。荷主 ID から決定的に決まる。 */
    static String keyRef(String shipperId) {
        return "alias/cargo-tracker/shipper/" + shipperId;
    }

    /** 鍵を作る。既にあれば作らずそれを返す。 */
    byte[] createOrGet(String shipperId);

    /** 鍵を引く。破棄済みなら空を返す（例外にしない。復元が止まると業務が止まる）。 */
    Optional<byte[]> find(String shipperId);

    /** 鍵を破棄する。以後その荷主の個人情報は誰にも読めない。 */
    void destroy(String shipperId);
}
