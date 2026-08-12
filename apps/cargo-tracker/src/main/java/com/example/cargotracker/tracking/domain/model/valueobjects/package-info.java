/**
 * 追跡コンテキストの値オブジェクトと列挙。
 *
 * <p>**同一性を持たない。** 等価性は値で決まり、生成後は変わらない。
 * 業務のことばをここで型にする —— String と BigDecimal のまま持ち回ると、
 * 取り違えてもコンパイルが通る。
 *
 * <p>集約ルートは TrackingActivity である。貨物 1 件の現在地と例外を守る。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.tracking.domain.model.valueobjects;
