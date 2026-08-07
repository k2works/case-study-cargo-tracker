/**
 * 共有カーネルが指す実体（港マスタ）の読み取り。
 *
 * <p>{@code Location} は共有カーネルの要素であり、その実体を読む必要は
 * Routing にも Booking にもある。<strong>どちらか一方の BC に置くと、
 * 他方が同じ SQL をもう 1 本持つか、BC 間の直接参照になる。</strong>
 *
 * <p>共有カーネルそのものではない（ArchUnit ルール 6 の対象は
 * {@code shared.domain.model}）。
 */
package com.example.cargotracker.shared.infrastructure.repositories;
