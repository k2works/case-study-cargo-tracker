/**
 * 経路コンテキストの値オブジェクトと列挙。
 *
 * <p>**同一性を持たない。** 等価性は値で決まり、生成後は変わらない。
 * 業務のことばをここで型にする —— String と BigDecimal のまま持ち回ると、
 * 取り違えてもコンパイルが通る。
 *
 * <p>集約ルートは Voyage（航海）と BookingRouteProposal（経路提案）の 2 つである。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.routing.domain.model.valueobjects;
