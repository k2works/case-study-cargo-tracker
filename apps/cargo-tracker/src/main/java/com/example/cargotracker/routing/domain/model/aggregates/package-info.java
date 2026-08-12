/**
 * 経路コンテキストの集約ルートと、その識別子。
 *
 * <p>**一貫性の境界である。** 1 回の取引で守りきる範囲がここで決まる。
 * 外から変えてよいのは集約ルート経由だけであり、内側のエンティティを直接持ち出さない。
 *
 * <p>集約ルートは Voyage（航海）と BookingRouteProposal（経路提案）の 2 つである。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.routing.domain.model.aggregates;
