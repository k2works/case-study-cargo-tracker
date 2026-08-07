/**
 * 荷主コンテキストの読み取り（CQRS のクエリ側）。
 *
 * <p>絞り込みは<strong>すべて SQL 側で行う</strong>。読み込んでから Java で
 * filter すると、件数が増えたときに一覧を開くだけで全件が載る。
 */
package com.example.cargotracker.shipper.application.internal.queryservices;
