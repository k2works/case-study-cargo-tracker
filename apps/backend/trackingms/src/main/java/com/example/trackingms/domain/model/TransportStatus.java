package com.example.trackingms.domain.model;

/**
 * 輸送の状況。
 *
 * <p>IT6 で使うのは {@link #NOT_RECEIVED} だけである。荷役が始まってからの遷移は
 * US15 以降で足す。<strong>「まだ受け取っていない」は空欄ではなく意味のある状態</strong>
 * （[ADR-009]）——列を nullable にして後から必須にすると、IT6 で入った行が読めなくなる。
 */
public enum TransportStatus {
    NOT_RECEIVED
}
