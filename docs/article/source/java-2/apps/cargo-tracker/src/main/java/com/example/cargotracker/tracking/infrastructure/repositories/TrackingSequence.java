package com.example.cargotracker.tracking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 追跡番号の採番。
 *
 * <p><strong>MAX + 1 で数えない。</strong> 2 人が同時に発行すると両者が同じ最大値を
 * 読み、片方が一意制約で落ちる（IT1 持ち越しで荷主コードに起きた問題と同型）。
 * シーケンスはトランザクションの外で進むため、同時採番でも重複しない。
 *
 * <p><strong>MyBatis Mapper は {@code infrastructure/repositories} に置く</strong>
 * （{@code architecture_backend.md} のパッケージ構成）。IT6 では {@code acl} に
 * 置いており、そのために <strong>H2 方言スモークの対象から外れていた</strong>
 * （IT6 レビュー M5）。{@code nextval} は方言差の出る構文であり、
 * 検査の網から外れると「本番だけ動く」形になる。
 */
@Mapper
public interface TrackingSequence {

    /** 次の連番を払い出す。 */
    @Select("SELECT nextval('tracking_number_seq')")
    long next();
}
