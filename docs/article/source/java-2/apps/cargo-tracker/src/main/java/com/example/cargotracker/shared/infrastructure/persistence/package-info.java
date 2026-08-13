/**
 * 永続化の横断的な技術基盤（MyBatis の TypeHandler など）。
 *
 * <p><strong>共有カーネルではない。</strong> 業務の意味を持たない技術的な部品であり、
 * ArchUnit のルール 6（共有カーネルの範囲）の対象外である（ADR-005）。
 *
 * <p>UUID の TypeHandler は MyBatis 標準に存在しない。無い状態でマッパーを初期化すると
 * その時点で失敗する。
 */
package com.example.cargotracker.shared.infrastructure.persistence;
