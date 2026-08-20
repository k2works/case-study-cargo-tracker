package com.example.bookingms.domain.model;

/**
 * 輸送状況。
 *
 * <p>「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）。列を nullable にすると
 * 「受け取っていない」と「状況が不明」の区別がつかず、一覧の絞り込みが書けない。
 */
public enum TransportStatus {
    NOT_RECEIVED
}
