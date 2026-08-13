/**
 * 請求コンテキストの値オブジェクトと列挙。
 *
 * <p><strong>同一性を持たない。</strong> 等価性は値で決まり、生成後は変わらない。
 * 業務のことばをここで型にする —— String と BigDecimal のまま持ち回ると、
 * 取り違えてもコンパイルが通る。
 *
 *
 * <p>他の BC のクラスを直接参照してはならない（ArchUnit ルール 4）。
 */
package com.example.cargotracker.billing.domain.model.valueobjects;
