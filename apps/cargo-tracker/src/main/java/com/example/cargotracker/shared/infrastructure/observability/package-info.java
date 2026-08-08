/**
 * 運用が「起きたこと」に気づくための横断的な仕組み。
 *
 * <p><strong>共有カーネルではない。</strong> 業務の意味を持たない技術的な部品であり、
 * ArchUnit のルール 6（共有カーネルの範囲）の対象外である（ADR-005）。
 *
 * <p>ここに置くのは<strong>結果整合にしたことで利用者に返せなくなった失敗</strong>を
 * 数える手立てである（ADR-009 の代償）。BC ごとに別々の数え方をすると、
 * 運用手順書が BC の数だけ増える。数える場所を 1 つにすることで、
 * 手順書も 1 つで済む。
 */
package com.example.cargotracker.shared.infrastructure.observability;
