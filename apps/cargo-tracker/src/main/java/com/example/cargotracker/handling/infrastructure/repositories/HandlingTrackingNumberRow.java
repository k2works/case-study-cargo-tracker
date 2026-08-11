package com.example.cargotracker.handling.infrastructure.repositories;

/**
 * 荷役 ID と追跡番号の対（R5 でまとめ引きを入れたときの行）。
 *
 * <p><strong>列名の文字列で結果を読まない。</strong> 初版は
 * {@code Map<Long, Map<String, Object>>} を返し、{@code "tracking_number"} という
 * キーで値を取り出していた。列名の大文字小文字は DB で違いうるため、
 * <strong>本番で緑・ローカルで赤（またはその逆）</strong>が起きる形である。
 *
 * <p>実測では PostgreSQL・H2 とも小文字で一致しており、
 * <strong>いま壊れているわけではない</strong>。それでも型で受けるのは、
 * 追跡番号が黙って空になったとき<strong>承認する人がどの貨物の話か分からなくなる</strong>
 * ためである。静かに間違う経路は、落ちる経路より高くつく。
 */
public class HandlingTrackingNumberRow {

    private long id;
    private String trackingNumber;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
}
