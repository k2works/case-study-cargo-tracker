/**
 * 請求コンテキストの値オブジェクトと列挙。
 *
 * <p>**同一性を持たない。** 等価性は値で決まり、生成後は変わらない。
 * 業務のことばをここで型にする —— String と BigDecimal のまま持ち回ると、
 * 取り違えてもコンパイルが通る。
 *
 * <p>集約ルートは Invoice である。金額の確定と入金の記録を 1 つの境界で守る（ADR-016）。
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.billing.domain.model.valueobjects;
