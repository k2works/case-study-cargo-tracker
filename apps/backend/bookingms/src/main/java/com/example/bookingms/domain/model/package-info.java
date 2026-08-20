/**
 * 予約コンテキストのドメインモデル。集約ルート・値オブジェクト・列挙を置く。
 *
 * <p><strong>一貫性の境界である。</strong> 1 回の取引で守りきる範囲がここで決まる。
 * 現在の集約ルートは {@link com.example.bookingms.domain.model.Shipper} である
 * （貨物・旅程・見積は以降のイテレーションで加わる）。
 *
 * <p><strong>識別子の採番はここで行わない。</strong> 荷主コードは永続化の経路
 * （シーケンス）で採番する。集約側で MAX+1 のように自前採番すると、原因でない
 * 他の登録が UNIQUE 制約で落ちる。
 *
 * <p>フレームワークに依存してはならない（ArchUnit が検証する）。
 */
package com.example.bookingms.domain.model;
