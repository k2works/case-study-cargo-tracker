/**
 * 経路コンテキストの集約の内側で同一性を持つもの。
 *
 * <p><strong>単独では存在しない。</strong> 生存期間は集約ルートに従い、取り出すのも保存するのもルート経由である。
 *
 * <p><strong>パッケージを分けたことで、ルートだけに開いていた操作を公開せざるを得なくなった</strong>
 * （ADR-024）。呼んでよいのはいまも集約ルートだけである。
 *
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.routing.domain.model.entities;
