package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.Collection;
import java.util.List;

/**
 * 港マスタに登録されている港かを判定する出力ポート。
 *
 * <p><strong>外部キー違反を業務のエラーに変える。</strong> これが無いと、
 * 港マスタに無い港を入力したときに {@code DataIntegrityViolationException} が
 * そのまま伝播して 500 になり、利用者には「どの港が悪いのか」が分からない。
 *
 * <p>ACL ではなく BC 内の出力ポートだが、置き場所は
 * {@code outboundservices} に揃える（{@code architecture_backend.md} のパッケージ構成）。
 */
public interface KnownPorts {

    /**
     * 港マスタに存在しない港を返す。
     *
     * <p><strong>1 件ずつ問い合わせない。</strong> 区間の数だけ往復すると、
     * 寄港地の多い航海ほど登録が遅くなる。
     *
     * @param locations 確かめたい港
     * @return 存在しなかった港（すべて存在すれば空）
     */
    List<Location> findUnknown(Collection<Location> locations);
}
