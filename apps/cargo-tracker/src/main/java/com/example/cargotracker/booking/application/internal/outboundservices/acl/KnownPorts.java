package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.Collection;
import java.util.List;

/**
 * 港マスタに登録されている港かを判定する出力ポート。
 *
 * <p><strong>外部キー違反を業務のエラーに変える。</strong> 予約の出発地・目的地は
 * 経路探索（US08）の起点と終点になる。マスタに無い港を受け付けると、
 * 経路が見つからない理由が「便が無い」と読めてしまい、経路設計者は
 * 存在しない便を探し続けることになる。
 *
 * <p>Routing にも同名のポートがあるが、<strong>共有しない</strong>。
 * BC をまたいでポートを共有すると、片方の都合で他方の入口が変わる
 * （ADR-005・ArchUnit ルール 4）。読む先の港マスタが同じであることは、
 * インフラ側のアダプタが引き受ける。
 */
public interface KnownPorts {

    /**
     * 港マスタに存在しない港を返す。
     *
     * @param locations 確かめたい港
     * @return 存在しなかった港（すべて存在すれば空）
     */
    List<Location> findUnknown(Collection<Location> locations);
}
