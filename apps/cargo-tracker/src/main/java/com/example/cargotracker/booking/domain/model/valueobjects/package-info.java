/**
 * 予約コンテキストの値オブジェクトと列挙。
 *
 * <p>**同一性を持たない。** 等価性は値で決まり、生成後は変わらない。
 * 業務のことばをここで型にする —— String と BigDecimal のまま持ち回ると、
 * 取り違えてもコンパイルが通る。
 *
 * <p>集約ルートは Cargo である。予約状態と経路状態は別々に動く（遷移表 3）。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.booking.domain.model.valueobjects;
