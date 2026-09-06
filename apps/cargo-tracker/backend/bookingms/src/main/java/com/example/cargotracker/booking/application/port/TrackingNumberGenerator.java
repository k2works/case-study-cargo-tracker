package com.example.cargotracker.booking.application.port;

/**
 * 追跡番号を採る窓口（US14 / ADR-0010 決定 2）。
 *
 * <p><b>集約で採らない。</b> 集約が MAX+1 を採ると、同時に 2 件発行したときに同じ番号が
 * 出る（{@code booking_number}・{@code shipper_code} と同じ形で、採番はデータベースの
 * シーケンスに任せる）。集約は「発行してよいか」だけを判断し、番号は渡されたものを載せる。</p>
 *
 * <p><b>ポートにする。</b> 発行の入口（Controller）が投影のマッパーを直に触ると、
 * 層をまたぐ依存が増える。何を頼んでいるかが型で読めるようにする。</p>
 */
public interface TrackingNumberGenerator {

    /**
     * 次の追跡番号。<b>採ってから断られることがある</b>（二重発行を集約が断る）。
     *
     * <p>そのとき採った番号は使われずに飛ぶ。番号が連続しないことより、同じ番号が
     * 2 つ出ないことを優先する。</p>
     */
    String next();
}
